package org.midorinext.android.sync

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import org.midorinext.android.BuildConfig

internal class SyncTransport(server: String, private val network: SyncNetworkContext? = null) {
    val server: String = normalize(server)
    fun request(path: String, token: String? = null, method: String = "GET", body: JSONObject? = null,
                query: Map<String, String> = emptyMap(), timeoutMs: Int = 30_000): JSONObject {
        network?.check()
        requireSync(Regex("[a-zA-Z0-9/_-]+").matches(path), "invalid_request")
        val suffix = query.entries.joinToString("&") { encode(it.key) + "=" + encode(it.value) }
        val transportServer = if (server.startsWith("http://localhost:")) server.replace("http://localhost:", "http://127.0.0.1:") else server
        val connection = URI(transportServer + path + if (suffix.isEmpty()) "" else "?$suffix").toURL().openConnection() as HttpURLConnection
        network?.register(connection)
        network?.metrics?.requests?.incrementAndGet()
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = minOf(15_000, timeoutMs)
            connection.readTimeout = timeoutMs
            connection.useCaches = false
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MidoriAndroid/${BuildConfig.VERSION_NAME}")
            token?.let { requireSync(Regex("[A-Za-z0-9._~-]{1,4096}").matches(it), "invalid_session"); connection.setRequestProperty("Authorization", "Bearer $it") }
            body?.let {
                val bytes = it.toString().toByteArray()
                requireSync(bytes.size <= 4 * 1024 * 1024, "request_too_large")
                network?.metrics?.sentBytes?.addAndGet(bytes.size.toLong())
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { stream -> stream.write(bytes) }
            }
            val status = connection.responseCode
            requireSync(status !in 300..399, "redirect_rejected")
            if (status == 204) return JSONObject()
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    network?.check()
                    val count = input.read(chunk)
                    if (count < 0) break
                    requireSync(output.size() + count <= 2 * 1024 * 1024, "response_too_large")
                    output.write(chunk, 0, count)
                }
                output.toByteArray() } ?: byteArrayOf()
            network?.check()
            network?.metrics?.receivedBytes?.addAndGet(bytes.size.toLong())
            requireSync(bytes.size <= 2 * 1024 * 1024, "response_too_large")
            val data = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            if (status !in 200..299) throw SyncException(data?.optString("error")?.takeIf { it.matches(Regex("[a-z_]{1,80}")) }
                ?: when (status) { 401 -> "auth_required"; 429 -> "rate_limited"; else -> "http_error" }, status,
                connection.getHeaderField("Retry-After")?.toLongOrNull()?.coerceIn(0, 86400) ?: 0)
            requireSync(connection.contentType?.substringBefore(';') == "application/json" && data != null, "invalid_response")
            return data!!
        } catch (error: java.io.IOException) {
            network?.check()
            throw SyncException("network_error")
        } finally { if (network == null) connection.disconnect() else network.release(connection) }
    }

    fun watchNotifications(ticket: String, onChange: () -> Unit) {
        requireSync(ticket.matches(Regex("[0-9a-f]{64}")), "invalid_notification_ticket")
        network?.check()
        val connection = URI(server + "api/v1/sync/notifications/stream").toURL().openConnection() as HttpURLConnection
        network?.register(connection)
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Authorization", "MidoriNotification $ticket")
            val status = connection.responseCode
            if (status != 200) throw SyncException("notification_unavailable", status)
            requireSync(connection.contentType?.substringBefore(';') == "text/event-stream", "invalid_response")
            connection.inputStream.use { input ->
                val chunk = ByteArray(1024)
                var buffer = ""
                while (true) {
                    network?.check()
                    val count = input.read(chunk)
                    if (count < 0) break
                    buffer = (buffer + chunk.copyOf(count).toString(Charsets.UTF_8)).replace("\r\n", "\n")
                    requireSync(buffer.length <= 4096, "response_too_large")
                    var end = buffer.indexOf("\n\n")
                    while (end >= 0) {
                        val lines = buffer.substring(0, end).split('\n')
                        if ("event: changed" in lines && "data: {}" in lines) onChange()
                        buffer = buffer.substring(end + 2)
                        end = buffer.indexOf("\n\n")
                    }
                }
            }
        } catch (error: java.io.IOException) {
            network?.check()
            throw SyncException("network_error")
        } finally { if (network == null) connection.disconnect() else network.release(connection) }
    }
    companion object {
        const val DEFAULT_SERVER = "https://sync.astian.org/"

        fun normalize(input: String): String {
            val uri = runCatching { URI(input.trim()) }.getOrElse { throw SyncException("invalid_server") }
            val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT)
            val host = uri.host?.lowercase(java.util.Locale.ROOT)
            val local = BuildConfig.DEBUG && scheme == "http" && host in listOf("localhost", "127.0.0.1", "[::1]")
            requireSync((scheme == "https" || local) && !host.isNullOrEmpty() && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null && (uri.path.isNullOrEmpty() || uri.path == "/") &&
                (uri.port == -1 || uri.port in 1..65535), "invalid_server")
            val port = if (scheme == "https" && uri.port == 443 || scheme == "http" && uri.port == 80) -1 else uri.port
            return URI(scheme, null, host, port, "/", null, null).toASCIIString()
        }
        fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
