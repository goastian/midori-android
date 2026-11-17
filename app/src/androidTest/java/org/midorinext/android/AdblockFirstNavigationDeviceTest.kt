package org.midorinext.android

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.lib.state.ext.flow
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.adblock.*

class AdblockFirstNavigationDeviceTest {
    @Test
    fun firstPageBlocksItsFirstRequestWithoutWaitingInTheCaller(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val settings = app.adBlockSettings.get()
        val original = settings.current
        val source = "https://midori-first-navigation.invalid/filters"
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val leaks = AtomicInteger()
        var tabId = ""
        var sources: NativeFilterSources? = null
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = if (path.startsWith("/probe-first")) {
                            leaks.incrementAndGet()
                            "tracker reached the server"
                        } else """<!doctype html><main>First protected page</main><script>
                          fetch('/probe-first',{cache:'no-store'}).then(
                            ()=>document.title='unprotected',()=>document.title='protected');
                        </script>""".trimIndent()
                        val bytes = body.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        try {
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, false) }
            settings.setSiteLevel("127.0.0.1", null)
            settings.setLevel(BlockingLevel.TRACKERS_AND_ADS)
            settings.removeSource(source)
            assertTrue(settings.addSource(source, tracker = true))
            val directory = File(app.filesDir, "adblock-sources").apply { mkdirs() }
            File(directory, "${RustFilterEngine.sha256(source.toByteArray())}.txt").writeText("/probe-first${'$'}xmlhttprequest\n")
            settings.sourceUpdated(source, System.currentTimeMillis())
            app.getSharedPreferences("native_adblock_http", 0).edit().putInt("format_version", 3).commit()
            instrumentation.runOnMainSync {
                app.initializeBrowser("adblock_first_navigation", restoreSession = false)
                val feature = app.nativeContentBlockingFeature.get()
                sources = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
                tabId = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/fixture", selectTab = true)
                app.store.get().dispatch(EngineAction.CreateEngineSessionAction(tabId))
            }
            val state = withTimeout(30_000) { app.store.get().flow().first { state ->
                state.tabs.find { it.id == tabId }?.content?.title in listOf("protected", "unprotected")
            } }
            assertEquals("protected", state.tabs.first { it.id == tabId }.content.title)
            assertEquals("No first-navigation tracker reaches the server", 0, leaks.get())
        } finally {
            if (tabId.isNotEmpty()) app.store.get().dispatch(TabListAction.RemoveTabsAction(listOf(tabId)))
            if (sources != null) sources!!.removeCustom(source) else settings.removeSource(source)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, true) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, true) }
            settings.setSiteLevel("127.0.0.1", original.siteLevels["127.0.0.1"])
            settings.setLevel(original.level)
            server.close(); executor.shutdownNow()
        }
    }
}
