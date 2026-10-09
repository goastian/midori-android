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
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

/** GeckoView 157 requires a content-script bridge for DOM access; policy stays in the native blocker. */
@Singleton
class NativeContentBlockingFeature @Inject constructor(
    private val runtime: GeckoRuntime,
    private val store: BrowserStore,
    private val settings: AdBlockSettings,
    private val sources: NativeFilterSources,
    private val networkBlocker: NativeNetworkBlocker,
    private val filter: DesktopHostFilter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ports = mutableMapOf<EngineSession, Port>()
    private val adPorts = mutableMapOf<EngineSession, Port>()
    private var backgroundPort: WebExtension.Port? = null
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

    private val adMessageHandler = object : MessageHandler {
        override fun onPortConnected(port: Port) {
            val session = port.engineSession ?: return
            adPorts[session] = port
        }

        override fun onPortMessage(message: Any, port: Port) {
            if (message !is JSONObject) return
            when (message.optString("type")) {
                "ready" -> sendAdPolicy(port)
                "blocked_count" -> port.engineSession?.let { session ->
                    val tab = (store.state.tabs + store.state.customTabs).find { it.engineState.engineSession === session }
                    if (tab?.content?.url?.substringBefore('#') == message.optString("siteUrl").substringBefore('#')) {
                        filter.updateNetworkCount(session, message.optInt("count"))
                    }
                }
            }
        }

        override fun onPortDisconnected(port: Port) {
            if (adPorts[port.engineSession] === port) adPorts.remove(port.engineSession)
        }
    }

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            backgroundPort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message is JSONObject && message.optString("type") == "ready") sendNetworkPolicy()
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (backgroundPort === port) backgroundPort = null
                }
            })
        }

        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (message !is JSONObject || message.optString("type") != "network_request" ||
                sender.webExtension.id != EXTENSION_ID || sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION) return null
            val result = GeckoResult<Any>()
            scope.launch(Dispatchers.Default) {
                val blocked = runCatching { networkBlocker.blocks(message) }.getOrDefault(false)
                result.complete(JSONObject().put("cancel", blocked))
            }
            return result
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
                sources.revision,
            ) { sessions, config, revision -> Triple(sessions, config, revision) }
                .distinctUntilChanged()
                .collect { (sessions, _, _) ->
                    registerSessions(sessions)
                    ports.values.toList().forEach(::sendPolicy)
                    adPorts.values.toList().forEach(::sendAdPolicy)
                    sendNetworkPolicy()
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
            installError(IllegalStateException("Content blocking bridge installation returned no extension"))
            return
        }
        runOnUiThread {
            runtime.webExtensionController.setAllowedInPrivateBrowsing(installed, true).accept({ allowed ->
                val active = allowed ?: installed
                active.setMessageDelegate(backgroundDelegate, NETWORK_APP)
                extension = GeckoWebExtension(active, runtime)
                val state = store.state
                registerSessions((state.tabs + state.customTabs).mapNotNull { it.engineState.engineSession }.toSet())
            }, ::installError)
        }
    }

    private fun registerSessions(sessions: Set<EngineSession>) {
        val installed = extension ?: return
        (sessions - registeredSessions).forEach { session ->
            installed.registerContentMessageHandler(session, NATIVE_APP, messageHandler)
            installed.registerContentMessageHandler(session, AD_APP, adMessageHandler)
        }
        (registeredSessions - sessions).forEach { session ->
            installed.disconnectPort(NATIVE_APP, session)
            installed.disconnectPort(AD_APP, session)
            ports.remove(session)
            adPorts.remove(session)
        }
        registeredSessions = sessions
    }

    private fun sendNetworkPolicy() {
        if (!sources.ready.isCompleted) return
        val port = backgroundPort ?: return
        val config = settings.current
        runCatching {
            port.postMessage(JSONObject()
                .put("type", "network_policy")
                .put("level", config.level.name)
                .put("siteLevels", JSONObject(config.siteLevels.mapValues { it.value.name }))
                .put("revision", sources.revision.value))
        }.onFailure { Log.w(TAG, "Could not update network policy", it) }
    }

    private fun sendAdPolicy(port: Port) {
        val config = settings.current
        val enabled = config.levelFor(port.senderUrl()) == BlockingLevel.TRACKERS_AND_ADS && sources.hasAdSources(config)
        runCatching { port.postMessage(AdPlacementPolicy.message(enabled)) }
            .onFailure {
                adPorts.remove(port.engineSession)
                Log.w(TAG, "Could not update ad placement policy", it)
            }
    }

    private fun sendPolicy(port: Port) {
        runCatching { port.postMessage(CookieBannerPolicy.message(settings.current, port.senderUrl())) }
            .onFailure {
                ports.remove(port.engineSession)
                Log.w(TAG, "Could not send cookie banner policy", it)
            }
    }

    private fun installError(error: Throwable?) {
        Log.e(TAG, "Could not install content blocking bridge", error)
    }

    companion object {
        private const val TAG = "NativeContentBlocking"
        const val EXTENSION_ID = "midori-native-adblock@astian.org"
        private const val LOCATION = "resource://android/assets/adblock/content/"
        private const val NATIVE_APP = "midori_adblock"
        private const val AD_APP = "midori_content_blocking"
        private const val NETWORK_APP = "midori_network"
        private const val BUNDLE_VERSION = "1.1"
    }
}
