package org.midorinext.android.sync

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class SyncTransportTest {
    @Test fun default_laravel_rate_limit_responses_preserve_retry_after() {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val failure = AtomicReference<Throwable?>()
        val worker = thread(isDaemon = true) {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    assertEquals("GET /api/v1/ping HTTP/1.1", input.readLine())
                    while (!input.readLine().isNullOrEmpty()) { }
                    val body = "{\"message\":\"Too Many Attempts.\"}".toByteArray()
                    val headers = "HTTP/1.1 429 Too Many Requests\r\nContent-Type: application/json\r\nRetry-After: 17\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(headers.toByteArray() + body)
                }
            } catch (error: Throwable) { failure.set(error) }
        }
        try {
            try { SyncTransport("http://localhost:${server.localPort}/").request("api/v1/ping"); fail("rate limit accepted") }
            catch (error: SyncException) { assertEquals("rate_limited", error.code); assertEquals(429, error.status); assertEquals(17, error.retryAfter) }
            worker.join(5000)
            assertNull(failure.get())
        } finally { server.close(); worker.join(5000) }
    }
}
