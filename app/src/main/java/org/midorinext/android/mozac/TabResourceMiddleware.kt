package org.midorinext.android.mozac

import org.midorinext.android.diagnostics.ScenarioMetrics
import org.midorinext.android.diagnostics.ScenarioMetrics.Event
import java.lang.ref.WeakReference
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
) : Middleware<BrowserState, BrowserAction> {
    private var registered = false
    private var closed = false
    private var trimJob: Job? = null
    private var previousSelectedId: String? = null
    private var diagnosticState: BrowserState? = null
    private val priorities = mutableMapOf<EngineSession, EngineSession.SessionPriority>()
    private val checkingForms = mutableMapOf<String, EngineSession>()
    private val flushingState = mutableMapOf<String, EngineSession>()

    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        if (action is EngineAction.SuspendEngineSessionAction &&
            store.state.tabs.any { it.id == action.tabId && it.isProtected(store.state.selectedTabId) }
        ) return
        val initial = if (ScenarioMetrics.enabled) store.state else null
        next(action)
        if (initial != null) {
            val before = diagnosticState ?: initial
            val after = store.state
            diagnosticState = after
            val old = before.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            val current = after.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            ScenarioMetrics.count(Event.SESSION_CREATED, (current - old).size)
            ScenarioMetrics.count(Event.SESSION_CLOSED, (old - current).size)
            after.tabs.forEach { tab ->
                val previous = before.tabs.find { it.id == tab.id }
                if (tab.engineState.engineSession in (current - old) && previous?.engineState?.engineSessionState != null) {
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
                                    if (tab.mediaSessionState?.playbackState?.let { it != MediaSession.PlaybackState.UNKNOWN } == true) add("media")
                                    if (tab.id in checkingForms) add("form_check_pending")
                                    if (tab.id in flushingState) add("state_flush_pending")
                                })
                        }
                    }.orEmpty()
                }
                budget.register(this@TabResourceMiddleware) { scheduleTrim(store) }
            }
            budget.update(this@TabResourceMiddleware, store.state.tabs.filter { it.engineState.engineSession != null }.map {
                ResidentTab(it.id, maxOf(it.lastAccess, it.createdAt), it.isProtected(store.state.selectedTabId))
            })
            val state = store.state
            val liveSessions = state.tabs.mapNotNull { it.engineState.engineSession }.toSet()
            priorities.keys.retainAll(liveSessions)
            checkingForms.entries.removeAll { (id, session) ->
                state.tabs.none { it.id == id && it.engineState.engineSession === session }
            }
            flushingState.entries.removeAll { (id, session) ->
                state.tabs.none { it.id == id && it.engineState.engineSession === session }
            }
            if (previousSelectedId != state.selectedTabId) {
                state.tabs.find { it.id == previousSelectedId }?.let { tab ->
                    tab.engineState.engineSession?.let { session ->
                        if (tab.id !in checkingForms && tab.id !in flushingState) {
                            checkingForms[tab.id] = session
                            session.checkForFormData(adjustPriority = false)
                        }
                    }
                }
                previousSelectedId = state.selectedTabId
            }
            state.tabs.forEach { tab ->
                val session = tab.engineState.engineSession ?: return@forEach
                val priority = if (tab.isProtected(state.selectedTabId) ||
                    tab.id in checkingForms || tab.id in flushingState
                ) EngineSession.SessionPriority.HIGH else EngineSession.SessionPriority.DEFAULT
                if (priorities[session] != priority) {
                    session.updateSessionPriority(priority)
                    priorities[session] = priority
                }
            }
            when (action) {
                is ContentAction.UpdateHasFormDataAction -> {
                    val session = checkingForms.remove(action.tabId)
                    if (session != null && !action.containsFormData && action.tabId in candidates(store.state)) {
                        flushingState[action.tabId] = session
                        // Wait for the refreshed native state before closing the session.
                        session.flushSessionState()
                    }
                }
                is EngineAction.UpdateEngineSessionStateAction -> {
                    val session = flushingState.remove(action.tabId)
                    val tab = store.state.tabs.find { it.id == action.tabId }
                    if (session != null && tab?.engineState?.engineSession === session &&
                        tab.engineState.engineSessionState != null && action.tabId in candidates(store.state)
                    ) {
                        store.dispatch(EngineAction.SuspendEngineSessionAction(action.tabId))
                    }
                }
                else -> Unit
            }
            scheduleTrim(store)
        }
    }

    private fun scheduleTrim(store: Store<BrowserState, BrowserAction>) {
        if (trimJob?.isActive == true) return
        trimJob = scope.launch {
            delay(1_000)
            candidates(store.state).forEach { id ->
                if (id !in checkingForms && id !in flushingState) {
                    val session = store.state.tabs.find { it.id == id }?.engineState?.engineSession ?: return@forEach
                    checkingForms[id] = session
                    session.checkForFormData(adjustPriority = false)
                }
            }
        }
    }

    fun close() {
        closed = true
        trimJob?.cancel()
        budget.unregister(this)
        ScenarioMetrics.remove(this)
        registered = false
        priorities.clear()
        diagnosticState = null
        checkingForms.clear()
        flushingState.clear()
    }

    private fun candidates(state: BrowserState) = budget.candidates(this).filter { id ->
        state.tabs.any { it.id == id && it.engineState.engineSession != null && !it.isProtected(state.selectedTabId) }
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

internal fun TabSessionState.isProtected(selectedId: String?): Boolean = id == selectedId ||
    content.loading || content.hasFormData || content.pictureInPictureEnabled ||
    content.recordingDevices.isNotEmpty() || content.promptRequests.isNotEmpty() ||
    content.permissionRequestsList.isNotEmpty() || content.appPermissionRequestsList.isNotEmpty() ||
    mediaSessionState?.playbackState?.let { it != MediaSession.PlaybackState.UNKNOWN } == true
