package org.midorinext.android.mozac

import org.midorinext.android.diagnostics.ScenarioMetrics
import org.midorinext.android.diagnostics.ScenarioMetrics.Event
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

internal data class ResidentTab(val id: String, val lastAccess: Long, val protected: Boolean)

internal fun tabsToSuspend(tabs: List<ResidentTab>, limit: Int): List<String> = tabs
    .filterNot { it.protected }
    .sortedBy { it.lastAccess }
    .take((tabs.size - limit.coerceAtLeast(1)).coerceAtLeast(0))
    .map { it.id }

class TabResourceMiddleware(
    private val budget: BrowserMemoryBudget,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : Middleware<BrowserState, BrowserAction> {
    private var registered = false
    private var closed = false
    private var trimJob: Job? = null
    private var mediaReleaseJob: Job? = null
    private var mediaReleaseDeadline: Long? = null
    private var previousSelectedId: String? = null
    private var diagnosticState: BrowserState? = null
    private val priorities = mutableMapOf<EngineSession, EngineSession.SessionPriority>()
    private val checkingForms = mutableMapOf<String, EngineSession>()
    private val flushingState = mutableMapOf<String, EngineSession>()
    private val operationDeadlines = mutableMapOf<String, Long>()
    private val retryAfter = mutableMapOf<String, Long>()
    private data class PausedSession(val session: EngineSession, val deadline: Long)
    private val pausedSessions = ConcurrentHashMap<String, PausedSession>()

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        if (action is ContentAction.CheckForFormDataExceptionAction ||
            action is ContentAction.UpdatePriorityToDefaultAfterTimeoutAction
        ) {
            scope.launch {
                if (closed) return@launch
                if (action is ContentAction.CheckForFormDataExceptionAction) {
                    deferRetry(action.tabId)
                }
                updatePriorities(store.state)
                scheduleTrim(store)
            }
            return
        }
        if (action is EngineAction.SuspendEngineSessionAction &&
            store.state.tabs.any { it.id == action.tabId && it.isResourceProtected(store.state.selectedTabId) }
        ) return
        val initial = if (ScenarioMetrics.enabled) store.state else null
        next(action)
        if (initial != null) {
            val before = diagnosticState ?: initial
            val after = store.state
            diagnosticState = after
            val old = before.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            val current = after.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            val created = current - old
            val previousTabs = before.tabs.associateBy { it.id }
            ScenarioMetrics.count(Event.SESSION_CREATED, (current - old).size)
            ScenarioMetrics.count(Event.SESSION_CLOSED, (old - current).size)
            after.tabs.forEach { tab ->
                val previous = previousTabs[tab.id]
                if (tab.engineState.engineSession in created && previous?.engineState?.engineSessionState != null) {
                    ScenarioMetrics.count(Event.SESSION_RESTORED)
                }
                if (tab.content.loading && previous?.content?.loading == false) {
                    ScenarioMetrics.count(Event.LOAD_STARTED)
                    if (previous.content.url == tab.content.url) ScenarioMetrics.count(Event.SAME_URL_LOAD_STARTED)
                }
            }
        }
        if (!ScenarioMetrics.enabled) diagnosticState = null
        if (closed || !action.affectsTabResources()) return

        scope.launch {
            if (closed) return@launch
            if (!registered) {
                registered = true
                val reference = WeakReference(store)
                ScenarioMetrics.inventory(this@TabResourceMiddleware) {
                    reference.get()?.state?.let { state ->
                        state.tabs.map { tab ->
                            ScenarioMetrics.Session(tab.engineState.engineSession,
                                tab.engineState.engineSession == null && tab.engineState.engineSessionState != null,
                                buildList {
                                    if (tab.id == state.selectedTabId) add("selected")
                                    if (tab.content.loading) add("loading")
                                    if (tab.content.hasFormData) add("form")
                                    if (tab.content.pictureInPictureEnabled) add("pip")
                                    if (tab.content.recordingDevices.isNotEmpty()) add("capture")
                                    if (tab.content.promptRequests.isNotEmpty()) add("prompt")
                                    if (tab.content.permissionRequestsList.isNotEmpty() || tab.content.appPermissionRequestsList.isNotEmpty()) add("permission")
                                    if (tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING) add("media")
                                    if (tab.hasPauseGrace()) add("media_pause_grace")
                                    if (tab.id in checkingForms) add("form_check_pending")
                                    if (tab.id in flushingState) add("state_flush_pending")
                                })
                        }
                    }.orEmpty()
                }
                budget.register(this@TabResourceMiddleware) { scheduleTrim(store) }
            }
            val state = store.state
            val tabsById = state.tabs.associateBy { it.id }
            pausedSessions.entries.removeAll { (id, paused) ->
                val tab = tabsById[id]
                tab?.engineState?.engineSession !== paused.session ||
                    tab?.mediaSessionState?.playbackState != MediaSession.PlaybackState.PAUSED
            }
            state.tabs.forEach { tab ->
                val session = tab.engineState.engineSession
                if (session != null && tab.mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED) {
                    pausedSessions.getOrPut(tab.id) { PausedSession(session, now() + PAUSED_MEDIA_GRACE_MS) }
                }
            }
            publishBudget(state)
            val liveSessions = state.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            priorities.keys.retainAll(liveSessions)
            checkingForms.entries.removeAll { (id, session) ->
                tabsById[id]?.engineState?.engineSession !== session
            }
            flushingState.entries.removeAll { (id, session) ->
                tabsById[id]?.engineState?.engineSession !== session
            }
            operationDeadlines.keys.retainAll(checkingForms.keys + flushingState.keys)
            retryAfter.keys.retainAll(tabsById.keys)
            if (previousSelectedId != state.selectedTabId) {
                state.tabs.find { it.id == previousSelectedId }?.let { tab ->
                    tab.engineState.engineSession?.let { session ->
                        if (tab.id !in checkingForms && tab.id !in flushingState) {
                            checkForms(tab.id, session)
                        }
                    }
                }
                previousSelectedId = state.selectedTabId
            }
            when (action) {
                is ContentAction.UpdateHasFormDataAction -> {
                    val session = checkingForms.remove(action.tabId)
                    operationDeadlines.remove(action.tabId)
                    if (session != null && !action.containsFormData && action.tabId in candidates(store.state)) {
                        flushingState[action.tabId] = session
                        operationDeadlines[action.tabId] = now() + 5_000
                        // Wait for the refreshed native state before closing the session.
                        try {
                            session.flushSessionState()
                        } catch (error: Exception) {
                            deferRetry(action.tabId)
                            android.util.Log.w("MidoriMemory", "Could not flush tab state", error)
                        }
                    }
                }
                is EngineAction.UpdateEngineSessionStateAction -> {
                    val session = flushingState.remove(action.tabId)
                    operationDeadlines.remove(action.tabId)
                    val tab = store.state.tabs.find { it.id == action.tabId }
                    if (session != null && tab?.engineState?.engineSession === session &&
                        tab.engineState.engineSessionState != null && action.tabId in candidates(store.state)
                    ) {
                        store.dispatch(EngineAction.SuspendEngineSessionAction(action.tabId))
                    }
                }
                else -> Unit
            }
            updatePriorities(store.state)
            scheduleTrim(store)
            scheduleMediaRelease(store)
        }
    }

    private fun TabSessionState.hasPauseGrace(): Boolean {
        if (mediaSessionState?.playbackState != MediaSession.PlaybackState.PAUSED || engineState.engineSession == null) return false
        val paused = pausedSessions[id]?.takeIf { it.session === engineState.engineSession }
        return paused == null || paused.deadline > now()
    }

    private fun TabSessionState.isResourceProtected(selectedId: String?) = isProtected(selectedId, hasPauseGrace())

    private fun publishBudget(state: BrowserState) {
        budget.update(this, state.tabs.filter { it.engineState.engineSession != null }.map {
            ResidentTab(it.id, maxOf(it.lastAccess, it.createdAt), it.isResourceProtected(state.selectedTabId))
        })
    }

    private fun scheduleMediaRelease(store: Store<BrowserState, BrowserAction>) {
        val deadline = pausedSessions.values.map { it.deadline }.filter { it > now() }.minOrNull()
        if (deadline == mediaReleaseDeadline && mediaReleaseJob?.isActive == true) return
        mediaReleaseJob?.cancel()
        mediaReleaseDeadline = deadline
        mediaReleaseJob = deadline?.let {
            scope.launch {
                delay((it - now()).coerceAtLeast(1))
                if (closed) return@launch
                mediaReleaseJob = null
                mediaReleaseDeadline = null
                publishBudget(store.state)
                scheduleTrim(store)
                scheduleMediaRelease(store)
            }
        }
    }

    private fun scheduleTrim(store: Store<BrowserState, BrowserAction>) {
        if (closed || trimJob?.isActive == true) return
        trimJob = scope.launch {
            delay(1_000)
            operationDeadlines.filterValues { it <= now() }.keys.toList().forEach(::deferRetry)
            retryAfter.entries.removeAll { it.value <= now() }
            candidates(store.state).forEach { id ->
                if (id !in checkingForms && id !in flushingState) {
                    val session = store.state.tabs.find { it.id == id }?.engineState?.engineSession ?: return@forEach
                    checkForms(id, session)
                }
            }
            updatePriorities(store.state)
            trimJob = null
            if (operationDeadlines.isNotEmpty() || retryAfter.isNotEmpty()) scheduleTrim(store)
        }
    }

    private fun checkForms(id: String, session: EngineSession) {
        if ((retryAfter[id] ?: 0) > now()) return
        checkingForms[id] = session
        operationDeadlines[id] = now() + 5_000
        try {
            session.checkForFormData(adjustPriority = false)
        } catch (error: Exception) {
            deferRetry(id)
            android.util.Log.w("MidoriMemory", "Could not check tab form data", error)
        }
    }

    private fun deferRetry(id: String) {
        checkingForms.remove(id)
        flushingState.remove(id)
        operationDeadlines.remove(id)
        retryAfter[id] = now() + 30_000
    }

    private fun updatePriorities(state: BrowserState) {
        state.tabs.forEach { tab ->
            val session = tab.engineState.engineSession ?: return@forEach
            val priority = if (tab.needsHighPriority(state.selectedTabId) ||
                tab.id in checkingForms || tab.id in flushingState
            ) EngineSession.SessionPriority.HIGH else EngineSession.SessionPriority.DEFAULT
            if (priorities[session] != priority) {
                session.updateSessionPriority(priority)
                priorities[session] = priority
            }
        }
    }

    fun close() {
        closed = true
        trimJob?.cancel()
        mediaReleaseJob?.cancel()
        mediaReleaseJob = null
        mediaReleaseDeadline = null
        budget.unregister(this)
        ScenarioMetrics.remove(this)
        registered = false
        priorities.clear()
        diagnosticState = null
        checkingForms.clear()
        flushingState.clear()
        operationDeadlines.clear()
        retryAfter.clear()
        pausedSessions.clear()
    }

    private fun candidates(state: BrowserState): List<String> {
        val eligible = state.tabs.asSequence().filter {
            it.engineState.engineSession != null && !it.isResourceProtected(state.selectedTabId) &&
                (retryAfter[it.id] ?: 0) <= now()
        }.map { it.id }.toSet()
        return budget.candidates(this).filter { it in eligible }
    }

    private companion object {
        const val PAUSED_MEDIA_GRACE_MS = 5_000L
    }
}

private fun BrowserAction.affectsTabResources(): Boolean = when (this) {
    is TabListAction, is EngineAction, is MediaSessionAction, is AppLifecycleAction,
    is SystemAction.LowMemoryAction, is ContentAction.UpdateHasFormDataAction,
    is ContentAction.UpdateLoadingStateAction, is ContentAction.PictureInPictureChangedAction,
    is ContentAction.UpdatePromptRequestAction, is ContentAction.ConsumePromptRequestAction,
    is ContentAction.ReplacePromptRequestAction, is ContentAction.SetRecordingDevices,
    is ContentAction.UpdatePermissionsRequest, is ContentAction.ConsumePermissionsRequest,
    is ContentAction.ClearPermissionRequests, is ContentAction.UpdateAppPermissionsRequest,
    is ContentAction.ConsumeAppPermissionsRequest, is ContentAction.ClearAppPermissionRequests -> true
    else -> false
}

internal fun TabSessionState.isProtected(selectedId: String?, pauseGrace: Boolean = false): Boolean = id == selectedId ||
    content.loading || content.hasFormData || content.pictureInPictureEnabled ||
    content.recordingDevices.isNotEmpty() || content.promptRequests.isNotEmpty() ||
    content.permissionRequestsList.isNotEmpty() || content.appPermissionRequestsList.isNotEmpty() ||
    mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING ||
    (pauseGrace && mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED)

internal fun TabSessionState.needsHighPriority(selectedId: String?): Boolean = id == selectedId ||
    content.hasFormData || content.pictureInPictureEnabled || content.recordingDevices.isNotEmpty() ||
    mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
