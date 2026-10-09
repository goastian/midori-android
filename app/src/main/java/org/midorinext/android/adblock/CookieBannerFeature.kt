package org.midorinext.android.adblock

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.webextension.GeckoWebExtension
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

/** GeckoView 157 requires a content-script bridge for DOM access; policy stays in the native blocker. */
@Singleton
class CookieBannerFeature @Inject constructor(
    private val runtime: GeckoRuntime,
    private val store: BrowserStore,
    private val settings: AdBlockSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ports = mutableMapOf<EngineSession, Port>()
    private var registeredSessions = emptySet<EngineSession>()
    private var extension: GeckoWebExtension? = null
    private var started = false

    private val messageHandler = object : MessageHandler {
        override fun onPortConnected(port: Port) {
            val session = port.engineSession ?: return
            ports[session] = port
        }

        override fun onPortMessage(message: Any, port: Port) {
            if (message is JSONObject && message.optString("type") == "ready") sendPolicy(port)
        }

        override fun onPortDisconnected(port: Port) {
            if (ports[port.engineSession] === port) ports.remove(port.engineSession)
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                store.flow().map { state ->
                    (state.tabs + state.customTabs).mapNotNull { it.engineState.engineSession }.toSet()
                },
                settings.state,
            ) { sessions, config -> sessions to config }
                .distinctUntilChanged()
                .collect { (sessions, _) ->
                    registerSessions(sessions)
                    ports.values.toList().forEach(::sendPolicy)
                }
        }
        runtime.webExtensionController.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ installed ->
            if (installed != null && installed.metaData.version != BUNDLE_VERSION) {
                runtime.webExtensionController.installBuiltIn(LOCATION).accept(::allowPrivateBrowsing, ::installError)
            } else {
                allowPrivateBrowsing(installed)
            }
        }, ::installError)
    }

    private fun allowPrivateBrowsing(installed: WebExtension?) {
        if (installed == null) {
            installError(IllegalStateException("Cookie banner bridge installation returned no extension"))
            return
        }
        runOnUiThread {
            runtime.webExtensionController.setAllowedInPrivateBrowsing(installed, true).accept({ allowed ->
                extension = GeckoWebExtension(allowed ?: installed, runtime)
                val state = store.state
                registerSessions((state.tabs + state.customTabs).mapNotNull { it.engineState.engineSession }.toSet())
            }, ::installError)
        }
    }

    private fun registerSessions(sessions: Set<EngineSession>) {
        val installed = extension ?: return
        (sessions - registeredSessions).forEach { session ->
            installed.registerContentMessageHandler(session, NATIVE_APP, messageHandler)
        }
        (registeredSessions - sessions).forEach { session ->
            installed.disconnectPort(NATIVE_APP, session)
            ports.remove(session)
        }
        registeredSessions = sessions
    }

    private fun sendPolicy(port: Port) {
        runCatching { port.postMessage(CookieBannerPolicy.message(settings.current, port.senderUrl())) }
            .onFailure {
                ports.remove(port.engineSession)
                Log.w(TAG, "Could not send cookie banner policy", it)
            }
    }

    private fun installError(error: Throwable?) {
        Log.e(TAG, "Could not install cookie banner bridge", error)
    }

    companion object {
        private const val TAG = "CookieBannerFeature"
        const val EXTENSION_ID = "midori-native-adblock@astian.org"
        private const val LOCATION = "resource://android/assets/adblock/content/"
        private const val NATIVE_APP = "midori_adblock"
        private const val BUNDLE_VERSION = "1.0"
    }
}
