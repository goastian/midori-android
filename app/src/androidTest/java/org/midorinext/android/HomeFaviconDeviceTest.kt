package org.midorinext.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.icons.Icon
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.Response
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.newtab.HomeFaviconRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class HomeFaviconDeviceTest {
    @Test
    fun selectsLargeManifestIconAndLoadsCachedIconWithoutNetwork(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication
        val counts = ConcurrentHashMap<String, AtomicInteger>()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val image = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${server.localPort}/"
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        while (!reader.readLine().isNullOrEmpty()) { }
                        counts.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                        val body = when (path) {
                            "/" -> """<head><link rel=icon href=/small.png sizes=16x16>
                                <link rel=manifest href=/app/manifest.json></head>""".toByteArray()
                            "/app/manifest.json" -> """{"icons":[{"src":"../large.png","sizes":"512x512","type":"image/png"}]}""".toByteArray()
                            "/large.png" -> image
                            else -> byteArrayOf()
                        }
                        val type = if (path.endsWith(".png")) "image/png" else "text/plain; charset=utf-8"
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 ${if (body.isEmpty()) "404 Not Found" else "200 OK"}\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        try {
            val repository = HomeFaviconRepository(app, app.client, app.memoryBudget)
            val icon = repository.load(url)
            assertNotNull(icon)
            assertTrue("Decoded favicon should remain sharp at high density", icon!!.bitmap.width >= 128)
            assertEquals(1, counts["/"]?.get())
            assertEquals(1, counts["/app/manifest.json"]?.get())
            assertEquals(1, counts["/large.png"]?.get())
            assertNull(counts["/small.png"])
            val networkForbidden = object : Client() {
                override fun fetch(request: Request): Response = throw AssertionError("Cached favicon requested network")
            }
            val cached = HomeFaviconRepository(app, dagger.Lazy { networkForbidden }, app.memoryBudget).load(url)
            assertNotNull(cached)
            assertTrue(cached!!.source == Icon.Source.MEMORY || cached.source == Icon.Source.DISK)
            assertEquals(icon.bitmap.width, cached.bitmap.width)
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }
}
