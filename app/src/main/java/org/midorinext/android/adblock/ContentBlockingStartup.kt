package org.midorinext.android.adblock

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

@Singleton
class ContentBlockingStartup @Inject constructor(private val settings: AdBlockSettings) {
    private val applied = MutableStateFlow(false)
    internal val canNavigate get() = applied.value || !settings.current.hasBlockingSites()

    internal fun markReady() { applied.value = true }

    suspend fun awaitReady() {
        combine(applied, settings.state) { ready, config -> ready || !config.hasBlockingSites() }.first { it }
    }
}

class ContentBlockingStartupMiddleware(
    private val startup: ContentBlockingStartup,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : Middleware<BrowserState, BrowserAction> {
    private val pending = linkedMapOf<Pair<String, Class<*>>, BrowserAction>()
    private var waiting = false
    private var saturated = 0L

    override fun invoke(store: Store<BrowserState, BrowserAction>, next: (BrowserAction) -> Unit, action: BrowserAction) {
        if (startup.canNavigate && synchronized(pending) { pending.isEmpty() }) {
            next(action)
            return
        }
        val tabId = when (action) {
            is EngineAction.CreateEngineSessionAction -> action.tabId
            is EngineAction.LoadUrlAction -> action.tabId
            else -> null
        }
        val tab = (store.state.tabs + store.state.customTabs).find { it.id == tabId }
        val url = (action as? EngineAction.LoadUrlAction)?.url ?: tab?.content?.url.orEmpty()
        val webPage = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
        if (tabId == null || startup.canNavigate || !webPage) {
            next(action)
            synchronized(pending) {
                val tabs = (store.state.tabs + store.state.customTabs).mapTo(hashSetOf()) { it.id }
                pending.keys.removeAll { it.first !in tabs }
            }
            return
        }
        synchronized(pending) {
            val key = tabId to action.javaClass
            if (key !in pending && pending.size >= 128) {
                if (++saturated == 1L || saturated % 128 == 0L) Log.e("NativeContentBlocking", "Startup queue saturated: $saturated actions rejected")
                return
            }
            val previous = pending[key]
            pending[key] = if (action is EngineAction.CreateEngineSessionAction && previous is EngineAction.CreateEngineSessionAction) {
                action.copy(followupAction = action.followupAction ?: previous.followupAction)
            } else action
            if (waiting) return
            waiting = true
        }
        scope.launch {
            try {
                while (true) {
                    if (withTimeoutOrNull(5_000) { startup.awaitReady(); true } != true) {
                        Log.e("NativeContentBlocking", "Navigation is waiting for content blocking initialization")
                        startup.awaitReady()
                    }
                    val actions = synchronized(pending) {
                        if (pending.isEmpty()) {
                            waiting = false
                            return@launch
                        }
                        pending.values.toList().also { pending.clear() }
                    }
                    actions.forEach { queued -> store.dispatch(queued) }
                }
            } catch (error: Throwable) {
                synchronized(pending) { waiting = false }
                throw error
            }
        }
    }
}
