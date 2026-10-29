package org.midorinext.android.adblock

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.engine.gecko.webextension.GeckoWebExtension
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.content.blocking.Tracker
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton
import org.midorinext.android.BuildConfig

/** GeckoView 157 requires a content-script bridge for DOM access; policy stays in the native blocker. */
@Singleton
class NativeContentBlockingFeature @Inject constructor(
    private val runtime: GeckoRuntime,
    private val store: BrowserStore,
    private val settings: AdBlockSettings,
    private val sources: NativeFilterSources,
    private val networkBlocker: NativeNetworkBlocker,
    private val filter: DesktopHostFilter,
    private val statistics: dagger.Lazy<BlockingStatistics>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ports = mutableMapOf<EngineSession, Port>()
    private val adPorts = mutableSetOf<Port>()
    private val adPortIds = mutableMapOf<String, Port>()
    private val cosmeticTokens = mutableMapOf<Port, Pair<List<String>, List<String>>>()
    private var backgroundPort: WebExtension.Port? = null
    private var registeredSessions = emptySet<EngineSession>()
    private val trackingObservers = mutableMapOf<EngineSession, EngineSession.Observer>()
    private var extension: GeckoWebExtension? = null
    private var started = false
    private val bridgeReady = CompletableDeferred<Unit>()
    private val appliedPolicy = MutableStateFlow<Pair<AdBlockConfiguration, Long>?>(null)
    private val sentPolicies = linkedMapOf<Long, AdBlockConfiguration>()
    private var policySequence = 0L
    private data class PolicyKey(val port: WebExtension.Port, val config: AdBlockConfiguration, val revision: Long)
    private var pendingNetworkPolicy: PolicyKey? = null
    private var sentNetworkPolicy: PolicyKey? = null
    val ready: Deferred<Unit> = bridgeReady

    private data class NetworkRequest(val message: JSONObject, val port: WebExtension.Port, val queuedAt: Long)
    private val networkRequests = Channel<NetworkRequest>(128)
    private val cosmeticSignal = Channel<Unit>(Channel.CONFLATED)
    private val dirtyCosmeticPorts = linkedSetOf<Port>()
    private var networkPolicyJob: Job? = null
    private var saturatedRequests = 0L
    private var networkQueuePeak = 0
    private var networkPending = 0
    private var maxNetworkWaitMs = 0L

    init {
        scope.launch {
            for (request in networkRequests) {
                networkPending--
                if (backgroundPort !== request.port) continue
                val wait = android.os.SystemClock.elapsedRealtime() - request.queuedAt
                maxNetworkWaitMs = maxOf(maxNetworkWaitMs, wait)
                if (wait >= 1_200L) {
                    replyNetwork(request.message, request.port, JSONObject().put("cancel", true).put("saturated", true))
                    continue
                }
                val decision = withContext(Dispatchers.Default) {
                    runCatching { networkBlocker.decision(request.message) }
                        .getOrElse { JSONObject().put("cancel", false) }
                }
                if (backgroundPort === request.port) replyNetwork(request.message, request.port, decision)
            }
        }
        scope.launch {
            for (signal in cosmeticSignal) {
                while (dirtyCosmeticPorts.isNotEmpty()) {
                    val port = dirtyCosmeticPorts.first()
                    dirtyCosmeticPorts.remove(port)
                    if (port in adPorts) try {
                        calculateAdPolicy(port)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Could not calculate ad placement policy", error)
                    }
                }
            }
        }
    }

    internal data class WorkState(val pendingRequests: Int, val peakPendingRequests: Int,
        val saturatedRequests: Long, val maxWaitMs: Long, val dirtyPorts: Int)

    internal fun workState() = WorkState(networkPending, networkQueuePeak, saturatedRequests, maxNetworkWaitMs, dirtyCosmeticPorts.size)

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
            if (port.engineSession == null) return
            adPorts.add(port)
            val id = java.util.UUID.randomUUID().toString()
            adPortIds[id] = port
            runCatching { port.postMessage(JSONObject().put("type", "content_port").put("portId", id)) }
                .onFailure { forgetAdPort(port) }
        }

        override fun onPortMessage(message: Any, port: Port) {
            if (message !is JSONObject) return
            val connected = adPortIds[message.optString("portId")] ?: return
            if (connected.engineSession !== port.engineSession || connected.senderUrl() != port.senderUrl()) return
            when (message.optString("type")) {
                "ready" -> sendAdPolicy(connected)
                "cosmetic_query" -> {
                    fun tokens(key: String): List<String> {
                        val values = message.optJSONArray(key) ?: return emptyList()
                        return (0 until minOf(values.length(), 2_048)).mapNotNull { index ->
                            values.optString(index).takeIf { it.isNotEmpty() && it.length <= 256 }
                        }
                    }
                    cosmeticTokens[connected] = tokens("classes") to tokens("ids")
                    sendAdPolicy(connected)
                }
                "blocked_count" -> port.engineSession?.let { session ->
                    val tab = (store.state.tabs + store.state.customTabs).find { it.engineState.engineSession === session }
                    if (tab?.content?.url?.substringBefore('#') == message.optString("siteUrl").substringBefore('#')) {
                        filter.updateNetworkCount(session, message.optInt("count"))
                    }
                }
            }
        }

        override fun onPortDisconnected(port: Port) {
            forgetAdPort(port)
        }
    }

    private fun forgetAdPort(port: Port) {
        adPorts.remove(port)
        dirtyCosmeticPorts.remove(port)
        cosmeticTokens.remove(port)
        adPortIds.entries.removeAll { it.value === port }
    }

    private val backgroundDelegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            backgroundPort = port
            if (BuildConfig.DEBUG) Log.d(TAG, "Network background connected")
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message !is JSONObject) return
                    if (BuildConfig.DEBUG && message.optString("type") != "network_request") {
                        Log.d(TAG, "Background message: ${message.optString("type")}")
                    }
                    when (message.optString("type")) {
                        "ready" -> sendNetworkPolicy()
                        "network_request" -> handleNetworkRequest(message, port)
                        "network_policy_applied" -> {
                            val config = sentPolicies.remove(message.optLong("policyId", -1))
                            if (config == settings.current.networkPolicyConfiguration() && message.optLong("revision", -1) == sources.revision.value &&
                                sources.ready.isCompleted) {
                                appliedPolicy.value = config to sources.revision.value
                                bridgeReady.complete(Unit)
                            }
                        }
                        "scriptlet_policy_failed" -> Log.e(TAG, "Could not register bundled scriptlets: ${message.optString("error")}")
                        "blocked_total" -> statistics.get().recordNetworkSnapshot(
                            message.optString("reportId"), message.optLong("count", -1))
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (backgroundPort === port) {
                        backgroundPort = null
                        appliedPolicy.value = null
                        sentNetworkPolicy = null
                        pendingNetworkPolicy = null
                        networkPolicyJob?.cancel()
                    }
                }
            })
        }
    }

    private fun handleNetworkRequest(message: JSONObject, port: WebExtension.Port) {
        val requestId = message.optLong("requestId", -1)
        if (requestId <= 0) return
        val request = NetworkRequest(message, port, android.os.SystemClock.elapsedRealtime())
        if (networkRequests.trySend(request).isSuccess) {
            networkPending++
            networkQueuePeak = maxOf(networkQueuePeak, networkPending)
        } else {
            saturatedRequests++
            if (saturatedRequests == 1L || saturatedRequests % 128 == 0L) Log.w(TAG, "Network queue saturated: $saturatedRequests requests cancelled")
            replyNetwork(message, port, JSONObject().put("cancel", true).put("saturated", true))
        }
    }

    private fun replyNetwork(message: JSONObject, port: WebExtension.Port, decision: JSONObject) {
        runCatching {
            port.postMessage(decision.put("type", "network_response").put("requestId", message.optLong("requestId")))
        }.onFailure { Log.w(TAG, "Could not send network decision", it) }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            sources.ready.await()
            sendNetworkPolicy()
        }
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
                    adPorts.toList().forEach(::sendAdPolicy)
                    sendNetworkPolicy()
                }
        }
        runtime.webExtensionController.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ installed ->
            if (installed != null && installed.metaData.version != BuildConfig.ADBLOCK_BUNDLE_VERSION) {
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
        val state = store.state
        val regularSessions = (state.tabs + state.customTabs).filterNot { it.content.private }
            .mapNotNull { it.engineState.engineSession }.toSet()
        (trackingObservers.keys - regularSessions).forEach { session ->
            trackingObservers.remove(session)?.let(session::unregister)
        }
        (regularSessions - trackingObservers.keys).forEach { session ->
            val observer = object : EngineSession.Observer {
                override fun onTrackerBlocked(tracker: Tracker) {
                    statistics.get().recordBlocked()
                }
            }
            trackingObservers[session] = observer
            session.register(observer)
        }
        val installed = extension ?: return
        (sessions - registeredSessions).forEach { session ->
            installed.registerContentMessageHandler(session, NATIVE_APP, messageHandler)
            installed.registerContentMessageHandler(session, AD_APP, adMessageHandler)
        }
        (registeredSessions - sessions).forEach { session ->
            installed.disconnectPort(NATIVE_APP, session)
            installed.disconnectPort(AD_APP, session)
            ports.remove(session)
            adPorts.filter { it.engineSession === session }.forEach(::forgetAdPort)
        }
        registeredSessions = sessions
    }

    private fun sendNetworkPolicy() {
        if (!sources.ready.isCompleted) return
        val port = backgroundPort ?: return
        val config = settings.current
        val revision = sources.revision.value
        val key = PolicyKey(port, config.networkPolicyConfiguration(), revision)
        if (key == sentNetworkPolicy || key == pendingNetworkPolicy) return
        pendingNetworkPolicy = key
        networkPolicyJob?.cancel()
        networkPolicyJob = scope.launch {
            try {
                sources.prepareEngines(config)
                val scriptlets = withContext(Dispatchers.Default) { sources.scriptletPolicy(config) }
                if (backgroundPort !== port || key.config != settings.current.networkPolicyConfiguration() || revision != sources.revision.value) return@launch
                val policyId = ++policySequence
                if (BuildConfig.DEBUG) Log.d(TAG, "Sending policy $policyId, revision $revision, ${scriptlets.length} characters of scriptlet rules")
                sentPolicies[policyId] = key.config
                while (sentPolicies.size > 8) sentPolicies.remove(sentPolicies.keys.first())
                runCatching {
                    port.postMessage(JSONObject()
                        .put("type", "network_policy")
                        .put("level", config.level.name)
                        .put("siteLevels", JSONObject(config.siteLevels.mapValues { it.value.name }))
                        .put("revision", revision).put("policyId", policyId).put("scriptlets", scriptlets))
                    sentNetworkPolicy = key
                }.onFailure { Log.w(TAG, "Could not update network policy", it) }
            } finally { if (pendingNetworkPolicy == key) pendingNetworkPolicy = null }
        }
    }

    suspend fun awaitConfiguration(config: AdBlockConfiguration = settings.current): Boolean =
        withTimeoutOrNull(5_000) {
            appliedPolicy.first { it != null && it.first == config.networkPolicyConfiguration() && it.second == sources.revision.value }
            true
        } ?: false

    private fun sendAdPolicy(port: Port) {
        if (port !in adPorts) return
        dirtyCosmeticPorts.add(port)
        cosmeticSignal.trySend(Unit)
    }

    private suspend fun calculateAdPolicy(port: Port) {
        val config = settings.current
        val revision = sources.revision.value
        val frameUrl = port.senderUrl()
        val topUrl = (store.state.tabs + store.state.customTabs)
            .find { it.engineState.engineSession === port.engineSession }?.content?.url ?: frameUrl
        val level = config.levelFor(topUrl)
        val enabled = level == BlockingLevel.TRACKERS_AND_ADS && sources.hasAdSources(config)
        val tokens = cosmeticTokens[port] ?: (emptyList<String>() to emptyList<String>())
        sources.ready.await()
        if (config != settings.current || revision != sources.revision.value) return
        val policy = withContext(Dispatchers.Default) {
            val top = sources.cosmeticRules(config, topUrl, level)
            val frame = if (frameUrl == topUrl) top else sources.cosmeticRules(config, frameUrl, level)
            val native = if (enabled) sources.nativeCosmetics(config, frameUrl, tokens.first, tokens.second) else null
            val nativeTop = if (enabled && frameUrl != topUrl) sources.nativeCosmetics(config, topUrl, emptyList(), emptyList()) else native
            val selection = if (native == null) frame else {
                val selectors = native.getJSONArray("hide_selectors")
                val exceptions = native.getJSONArray("exceptions")
                frame.copy(rules = (0 until selectors.length()).map { index ->
                    CosmeticRule(selectors.getString(index), false, setOf("native"), emptySet())
                } + (0 until exceptions.length()).map { index ->
                    CosmeticRule(exceptions.getString(index), true, emptySet(), emptySet())
                })
            }
            AdPlacementPolicy.message(enabled, selection.copy(
                enabled = top.enabled && frame.enabled && native?.optBoolean("disabled") != true && nativeTop?.optBoolean("disabled") != true,
                genericEnabled = top.genericEnabled && frame.genericEnabled && native?.optBoolean("generichide") != true && nativeTop?.optBoolean("generichide") != true,
            )).put("procedural", native?.optJSONArray("procedural_actions") ?: org.json.JSONArray())
        }
        if (port !in adPorts || config != settings.current || revision != sources.revision.value ||
            tokens != (cosmeticTokens[port] ?: (emptyList<String>() to emptyList<String>()))) return
        runCatching { port.postMessage(policy) }.onFailure {
            forgetAdPort(port)
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
    }
}
