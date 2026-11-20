package org.midorinext.android.lite

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.midorinext.android.BuildConfig
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteMode @Inject constructor(
    @ApplicationContext context: Context,
    private val runtime: dagger.Lazy<GeckoRuntime>,
) {
    internal enum class Status { OFF, CONNECTING, ACTIVE, DIRECT, UNAVAILABLE, STORAGE_ERROR }

    internal data class Savings(val original: Long, val saved: Long) {
        val fraction: Double get() = if (original == 0L) 0.0 else saved.toDouble() / original
    }

    internal data class State(
        val loaded: Boolean = false,
        val enabled: Boolean = false,
        val privateAvailable: Boolean = false,
        val status: Status = Status.OFF,
        val savings: Savings? = null,
        val compression: LiteCompressionProfile = LiteCompressionProfile.SAVER,
    ) {
        fun statusFor(privateBrowsing: Boolean): Status = when {
            !privateBrowsing || !enabled || status == Status.CONNECTING -> status
            !privateAvailable -> Status.UNAVAILABLE
            status == Status.DIRECT -> Status.ACTIVE
            else -> status
        }
    }

    private data class BrowserPolicy(
        val dataSaving: Boolean,
        val server: LiteSettings?,
        val imageRelay: Boolean,
        val requestSiteSavings: Boolean,
    )

    private val store = LiteSettingsStore(context)
    private val imageRelay = LiteImageRelay()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val storageLock = Mutex()
    private val preference = MutableStateFlow<LitePreference?>(null)
    private val connection = MutableStateFlow<WebExtension.Port?>(null)
    private val stateFlow = MutableStateFlow(State())
    private val savingsRefresh = Channel<Unit>(Channel.CONFLATED)
    private val serviceOrigin = LiteSettings.origin(BuildConfig.MIDORIPRESS_URL)
    private val enrollment = if (BuildConfig.MIDORIPRESS_ENROLL_URL.isBlank()) {
        serviceOrigin?.resolve("/api/v1/lite/register")
    } else LiteSettings.enrollment(BuildConfig.MIDORIPRESS_ENROLL_URL)
    internal val state = stateFlow.asStateFlow()
    private var appliedPolicy: BrowserPolicy? = null
    private var provisioning: Job? = null
    private var imageRequests = 0
    private var extensionFailed = false
    private var started = false

    private val delegate = object : WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message is JSONObject && message.optString("type") == "liteImage") relayImage(message, port)
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (connection.value === port) {
                        appliedPolicy = null
                        connection.value = null
                        if (state.value.enabled) publish(Status.CONNECTING)
                    }
                }
            })
            appliedPolicy = null
            connection.value = port
            publish(syncDirectPolicy())
        }
    }

    fun start() {
        if (started) return
        started = true
        val controller = runtime.get().webExtensionController
        controller.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ extension ->
            if (extension != null && extension.metaData.version != BuildConfig.LITE_BUNDLE_VERSION) {
                controller.installBuiltIn(LOCATION).accept(::attachExtension, { extensionUnavailable() })
            } else {
                attachExtension(extension)
            }
        }, { extensionUnavailable() })
        scope.launch {
            val saved = withContext(Dispatchers.IO) { runCatching { store.read() } }
            val settings = saved.getOrDefault(LitePreference())
            preference.value = settings.copy(server = settings.server?.takeIf { it.server == serviceOrigin })
            val status = syncDirectPolicy()
            publish(if (saved.isFailure) Status.STORAGE_ERROR else status)
            provisionIfNeeded()
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(preference, connection) { current, port -> current to port }
                    .collectLatest { (current, port) ->
                        val server = current?.server ?: return@collectLatest
                        if (port == null) return@collectLatest
                        var profileConfirmed = !imageRelay.configured
                        while (isActive) {
                            if (!profileConfirmed) {
                                profileConfirmed = withContext(Dispatchers.IO) {
                                    imageRelay.selectProfile(server.token, current.compression)
                                }
                            }
                            if (!current.enabled) {
                                if (profileConfirmed) return@collectLatest
                                delay(5_000L)
                                continue
                            }
                            val savings = withContext(Dispatchers.IO) { fetchSavings(server) }
                            val policy = BrowserPolicy(
                                dataSaving = true,
                                server = server.takeIf { savings != null && profileConfirmed },
                                imageRelay = imageRelay.configured,
                                requestSiteSavings = current.compression.requestSiteSavings,
                            )
                            if (applyPolicy(port, policy)) {
                                publish(if (savings != null) Status.ACTIVE else Status.DIRECT, savings)
                            } else {
                                publish(Status.UNAVAILABLE)
                            }
                            if (withTimeoutOrNull(10_000L) { savingsRefresh.receive() } != null) {
                                delay(150L)
                                savingsRefresh.tryReceive()
                            }
                        }
                    }
            }
        }
    }

    internal fun setEnabled(enabled: Boolean) {
        val current = preference.value ?: return
        update(current.copy(enabled = enabled))
        provisionIfNeeded()
    }

    internal fun setCompression(profile: LiteCompressionProfile) {
        val current = preference.value ?: return
        update(current.copy(compression = profile))
    }

    private fun provisionIfNeeded() {
        val current = preference.value ?: return
        if (!current.enabled || current.server != null || provisioning?.isActive == true || extensionFailed) return
        val origin = serviceOrigin ?: return
        val registerUrl = enrollment ?: return
        provisioning = scope.launch {
            val credentials = withContext(Dispatchers.IO) { register(origin, registerUrl) }
            val latest = preference.value ?: return@launch
            if (credentials != null) {
                update(latest.copy(server = credentials))
            }
        }
    }

    private fun update(next: LitePreference) {
        val old = preference.value ?: return
        if (old == next) return
        val server = next.server
        preference.value = next
        if (old.server !== server) stateFlow.update { it.copy(savings = null) }
        publish(syncDirectPolicy())
        scope.launch {
            val saved = storageLock.withLock {
                withContext(Dispatchers.IO) { runCatching { store.write(next) } }
            }
            if (saved.isFailure && preference.value == next) publish(Status.STORAGE_ERROR)
        }
    }

    private fun publish(status: Status, savings: Savings? = null) {
        val current = preference.value ?: return
        stateFlow.update {
            it.copy(
                loaded = true,
                enabled = current.enabled,
                status = status,
                savings = savings ?: it.savings,
                compression = current.compression,
            )
        }
    }

    private fun syncDirectPolicy(): Status {
        val current = preference.value
        val enabled = current?.enabled == true
        val port = connection.value
        val policy = BrowserPolicy(
            dataSaving = enabled,
            server = null,
            imageRelay = enabled && current?.server != null && imageRelay.configured,
            requestSiteSavings = enabled && current?.compression?.requestSiteSavings == true,
        )
        val sent = port?.let { applyPolicy(it, policy) }
        return when {
            !enabled -> Status.OFF
            extensionFailed || sent == false -> Status.UNAVAILABLE
            port == null -> Status.CONNECTING
            else -> Status.DIRECT
        }
    }

    private fun applyPolicy(port: WebExtension.Port, policy: BrowserPolicy): Boolean {
        if (appliedPolicy == policy) return true
        val server = policy.server
        val message = JSONObject().put("enabled", server != null).put("dataSaving", policy.dataSaving)
            .put("imageRelay", policy.imageRelay)
            .put("requestSiteSavings", policy.requestSiteSavings)
        if (server != null) {
            message.put("host", server.server.host.removeSurrounding("[", "]"))
            message.put("port", server.port)
            message.put("scheme", server.server.scheme)
            message.put("device", server.deviceId)
            message.put("token", server.token)
        }
        return runCatching {
            port.postMessage(message)
            appliedPolicy = policy
        }.isSuccess
    }

    private fun relayImage(message: JSONObject, port: WebExtension.Port) {
        val id = message.optLong("requestId", -1)
        if (id < 1 || connection.value !== port) return
        val current = preference.value
        val token = current?.server?.token
        if (current?.enabled != true || token == null || imageRequests >= 4) {
            sendImageResult(port, id, null)
            return
        }
        val address = message.optString("url")
        imageRequests++
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) { imageRelay.fetch(address, token, current.compression) }
                if (connection.value === port) {
                    val result = data.takeIf { preference.value === current }
                    sendImageResult(port, id, result)
                    if (result != null) savingsRefresh.trySend(Unit)
                }
            } finally {
                imageRequests--
            }
        }
    }

    private fun sendImageResult(port: WebExtension.Port, id: Long, data: String?) {
        runCatching { port.postMessage(JSONObject().put("type", "liteImageResult").put("requestId", id).put("data", data)) }
    }

    private fun attachExtension(extension: WebExtension?) {
        if (extension == null) {
            extensionUnavailable()
            return
        }
        runOnUiThread {
            if (extension.metaData.allowedInPrivateBrowsing) {
                connectExtension(extension)
            } else {
                runtime.get().webExtensionController.setAllowedInPrivateBrowsing(extension, true).accept({ allowed ->
                    connectExtension(allowed ?: extension)
                }, { connectExtension(extension) })
            }
        }
    }

    private fun connectExtension(extension: WebExtension) {
        stateFlow.update { it.copy(privateAvailable = extension.metaData.allowedInPrivateBrowsing) }
        extension.setMessageDelegate(delegate, NATIVE_APP)
    }

    private fun extensionUnavailable() {
        extensionFailed = true
        Log.w(TAG, "Lite browser extension could not be installed")
        if (state.value.enabled) publish(Status.UNAVAILABLE)
    }

    private fun register(origin: URI, endpoint: URI): LiteSettings? {
        val random = SecureRandom()
        var identifier: BigInteger
        do {
            identifier = BigInteger(128, random)
        } while (identifier.signum() == 0)
        val deviceId = "lite-$identifier"
        val body = JSONObject().put("device_id", deviceId).toString().toByteArray(Charsets.UTF_8)
        val data = requestJson(endpoint, HttpURLConnection.HTTP_CREATED) { request ->
            request.requestMethod = "POST"
            request.setRequestProperty("X-Midori-Lite", "1")
            request.setRequestProperty("Content-Type", "application/json")
            request.setFixedLengthStreamingMode(body.size)
            request.doOutput = true
            request.outputStream.use { it.write(body) }
        } ?: return null
        if (data.optString("device_id") != deviceId) return null
        return LiteSettings.parse(origin.toString(), deviceId, data.optString("token"))
    }

    private fun fetchSavings(server: LiteSettings): Savings? {
        val data = requestJson(server.server.resolve("/api/v1/lite/stats"), HttpURLConnection.HTTP_OK) { request ->
            request.setRequestProperty("Authorization", "Bearer ${server.token}")
        } ?: return null
        val original = data.optLong("original_bytes", -1)
        val sent = data.optLong("sent_bytes", -1)
        val saved = data.optLong("saved_bytes", Long.MIN_VALUE)
        if (original < 0 || sent < 0 || saved != original - sent) return null
        return Savings(original, saved.coerceAtLeast(0L))
    }

    private fun requestJson(
        endpoint: URI,
        expectedStatus: Int,
        configure: (HttpURLConnection) -> Unit,
    ): JSONObject? = runCatching {
        val request = endpoint.toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            request.connectTimeout = 3_000
            request.readTimeout = 3_000
            request.instanceFollowRedirects = false
            request.useCaches = false
            request.setRequestProperty("Accept", "application/json")
            configure(request)
            if (request.responseCode != expectedStatus) return@runCatching null
            request.inputStream.use { stream ->
                val buffer = ByteArray(1025)
                var size = 0
                while (size < buffer.size) {
                    val count = stream.read(buffer, size, buffer.size - size)
                    if (count < 0) break
                    size += count
                }
                require(size < buffer.size)
                JSONObject(buffer.decodeToString(endIndex = size))
            }
        } finally {
            request.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val TAG = "MidoriLite"
        const val LOCATION = "resource://android/assets/extensions/midori_lite/"
        const val EXTENSION_ID = "midori-lite@astian.org"
        const val NATIVE_APP = "midoriLite"
    }
}
