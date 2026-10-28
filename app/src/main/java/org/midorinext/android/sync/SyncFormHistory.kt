package org.midorinext.android.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SyncFormHistory @Inject constructor(private val runtime: dagger.Lazy<GeckoRuntime>) {
    private var port: WebExtension.Port? = null
    private var started = false
    private var ready = CompletableDeferred<Unit>()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Any>>()
    private val next = AtomicLong()
    private suspend fun start() = withContext(Dispatchers.Main) {
        if (started) return@withContext
        started = true
        val delegate = object : WebExtension.MessageDelegate {
            override fun onConnect(newPort: WebExtension.Port) {
                port = newPort
                newPort.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        if (message !is JSONObject) return
                        if (message.optBoolean("ready")) { ready.complete(Unit); return }
                        pending.remove(message.optLong("id"))?.let { result ->
                            if (message.has("error")) result.completeExceptionally(SyncException("form_history_unavailable"))
                            else result.complete(message.get("data"))
                        }
                    }
                    override fun onDisconnect(port: WebExtension.Port) {
                        this@SyncFormHistory.port = null
                        pending.values.forEach { it.completeExceptionally(SyncException("form_history_unavailable")) }
                        pending.clear()
                        if (ready.isCompleted) ready = CompletableDeferred()
                    }
                })
            }
        }
        val controller = runtime.get().webExtensionController
        val location = "resource://android/assets/extensions/midori_sync/"
        fun attach(extension: WebExtension?) {
            if (extension == null) ready.completeExceptionally(SyncException("form_history_unavailable"))
            else extension.setMessageDelegate(delegate, "midoriSyncStorage")
        }
        controller.installBuiltIn(location).accept(::attach,
            { ready.completeExceptionally(SyncException("form_history_unavailable")) })
    }
    suspend fun request(action: String, vararg fields: Pair<String, Any?>): Any {
        start()
        return withTimeoutOrNull(15_000) {
            ready.await()
            val id = next.incrementAndGet()
            val result = CompletableDeferred<Any>()
            pending[id] = result
            try {
                withContext(Dispatchers.Main) {
                    val active = port ?: throw SyncException("form_history_unavailable")
                    active.postMessage(obj("id" to id, "action" to action, *fields))
                }
                result.await()
            } finally { pending.remove(id) }
        } ?: throw SyncException("form_history_unavailable")
    }
}
