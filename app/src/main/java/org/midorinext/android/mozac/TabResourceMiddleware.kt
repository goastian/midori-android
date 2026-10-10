package org.midorinext.android.mozac

import android.content.ComponentCallbacks2
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
    private val foregroundLimit: Int = 5,
    private val backgroundLimit: Int = 2,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : Middleware<BrowserState, BrowserAction> {
    private var limit = foregroundLimit
    private var trimJob: Job? = null
    private var previousSelectedId: String? = null
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
        next(action)
        if (!action.affectsTabResources()) return

        scope.launch {
            when (action) {
                is AppLifecycleAction.ResumeAction -> limit = foregroundLimit
                is AppLifecycleAction.PauseAction -> limit = backgroundLimit
                is SystemAction.LowMemoryAction -> {
                    if (action.level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) limit = backgroundLimit
                }
                else -> Unit
            }
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
            trimJob?.cancel()
            trimJob = launch {
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
    }

    private fun candidates(state: BrowserState) = tabsToSuspend(
        state.tabs.filter { it.engineState.engineSession != null }.map {
            ResidentTab(it.id, maxOf(it.lastAccess, it.createdAt), it.isProtected(state.selectedTabId))
        },
        limit,
    )
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
