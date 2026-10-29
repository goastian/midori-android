package org.midorinext.android.suggest

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response

@Singleton
class SuggestionFetcher @Inject constructor(private val client: Client) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Semaphore(2)

    suspend fun fetch(url: String, private: Boolean = false): String? = withTimeoutOrNull(2_000) {
        suspendCancellableCoroutine { continuation ->
            val activeResponse = AtomicReference<Response?>()
            // Fetch is blocking. Cancellation must release the caller without waiting for its IO job.
            val job = scope.launch {
                try {
                    requests.withPermit {
                        ensureActive()
                        val request = Request(
                            url,
                            connectTimeout = 1L to TimeUnit.SECONDS,
                            readTimeout = 1_500L to TimeUnit.MILLISECONDS,
                            cookiePolicy = Request.CookiePolicy.OMIT,
                            private = private,
                        )
                        val text = client.fetch(request).use { response ->
                            activeResponse.set(response)
                            ensureActive()
                            if (response.status != 200) return@use null
                            if ((response.headers["Content-Length"]?.toLongOrNull() ?: 0) > MAX_BODY_BYTES) {
                                throw IOException("Suggestion response exceeds limit")
                            }
                            val bytes = response.body.useStream { input ->
                                val output = ByteArrayOutputStream(8 * 1024)
                                val buffer = ByteArray(8 * 1024)
                                while (true) {
                                    ensureActive()
                                    val count = input.read(buffer, 0, minOf(buffer.size, MAX_BODY_BYTES + 1 - output.size()))
                                    if (count < 0) break
                                    if (output.size() + count > MAX_BODY_BYTES) throw IOException("Suggestion response exceeds limit")
                                    output.write(buffer, 0, count)
                                }
                                output.toByteArray()
                            }
                            Response.Body(bytes.inputStream(), response.headers["Content-Type"]).string()
                        }
                        if (continuation.isActive) continuation.resume(text)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                } finally {
                    activeResponse.set(null)
                }
            }
            continuation.invokeOnCancellation {
                job.cancel()
                scope.launch { activeResponse.get()?.close() }
            }
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}
