package org.midorinext.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.lib.state.ext.flow
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.adblock.*

class NativeCosmeticsDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication

    @Test
    fun browserTabsApplyNativeGenericAndProceduralFiltersInNormalAndPrivateMode(): Unit = runBlocking {
        val store = app.store.get()
        withTimeout(30_000) { store.flow().first { it.restoreComplete } }
        val selected = store.state.selectedTabId
        val settings = app.adBlockSettings.get()
        val original = settings.current
        val feature = app.nativeContentBlockingFeature.get()
        val sources = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        val putRules = sources.javaClass.getDeclaredMethod("putRules", String::class.java, NativeFilterRules::class.java).apply { isAccessible = true }
        val sourceUrl = "https://midori-native-cosmetics-fixture.invalid/filters.txt"
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newCachedThreadPool()
        val tabs = mutableListOf<String>()
        executor.submit {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    executor.submit {
                        socket.use {
                            it.soTimeout = 5_000
                            val reader = it.getInputStream().bufferedReader()
                            reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val body = """<!doctype html><main id="story">Article</main>
                                <div class="generic-midori-ad">Banner</div>
                                <section id="sponsor"><div class="label">Sponsored</div></section>
                                <div class="overlay">Overlay</div><script>
                                setTimeout(() => document.body.insertAdjacentHTML('beforeend', '<div class="dynamic-midori-ad">Later banner</div>'), 250);
                                setInterval(() => {
                                  const dynamic = document.querySelector('.dynamic-midori-ad');
                                  if (!dynamic) return;
                                  const title = JSON.stringify({
                                    generic:getComputedStyle(document.querySelector('.generic-midori-ad')).display === 'none',
                                    sponsor:getComputedStyle(document.querySelector('#sponsor')).display === 'none',
                                    removed:!document.querySelector('.overlay'),
                                    dynamic:getComputedStyle(dynamic).display === 'none',
                                    content:getComputedStyle(document.querySelector('#story')).display !== 'none'
                                  });
                                  if (title !== document.title) document.title = title;
                                }, 100);
                                </script>""".trimIndent().toByteArray()
                            it.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body); flush()
                            }
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        suspend fun load(private: Boolean, enabled: Boolean) {
            var tabId = ""
            instrumentation.runOnMainSync {
                tabId = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/fixture", private = private, selectTab = true)
            }
            tabs += tabId
            val state = try { withTimeout(30_000) {
                store.flow().first { state ->
                    val title = state.tabs.find { it.id == tabId }?.content?.title.orEmpty()
                    if (!title.startsWith('{')) false else JSONObject(title).let { result ->
                        listOf("generic", "sponsor", "removed", "dynamic").all { result.optBoolean(it) == enabled } && result.optBoolean("content")
                    }
                }
            } } catch (error: Exception) {
                val tab = store.state.tabs.find { it.id == tabId }
                throw AssertionError("Cosmetic fixture did not settle, private=$private, enabled=$enabled, url=${tab?.content?.url}, title=${tab?.content?.title}", error)
            }
            val result = JSONObject(state.tabs.first { it.id == tabId }.content.title)
            assertTrue("Article remains visible: $result", result.getBoolean("content"))
            for (field in listOf("generic", "sponsor", "removed", "dynamic")) assertEquals("$field, private=$private: $result", enabled, result.getBoolean(field))
        }
        try {
            withTimeout(30_000) { sources.ready.await() }
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, false) }
            settings.setLevel(BlockingLevel.OFF)
            assertTrue(settings.addSource(sourceUrl, tracker = false))
            putRules.invoke(sources, sourceUrl, parseHostSource("""
                ##.generic-midori-ad
                ##.dynamic-midori-ad
                127.0.0.1##.label:has-text(Sponsored):upward(1)
                127.0.0.1##.overlay:remove()
            """.trimIndent()))
            settings.setSiteLevel("127.0.0.1", BlockingLevel.TRACKERS_AND_ADS)
            withTimeout(30_000) { feature.ready.await() }
            assertTrue(feature.awaitConfiguration())
            load(private = false, enabled = true)
            load(private = true, enabled = true)
            settings.setSiteLevel("127.0.0.1", BlockingLevel.OFF)
            assertTrue(feature.awaitConfiguration())
            load(private = false, enabled = false)
        } finally {
            store.dispatch(TabListAction.RemoveTabsAction(tabs))
            if (selected != null) store.dispatch(TabListAction.SelectTabAction(selected))
            server.close(); executor.shutdownNow()
            sources.removeCustom(sourceUrl)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, true) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, true) }
            settings.setSiteLevel("127.0.0.1", original.siteLevels["127.0.0.1"])
            settings.setLevel(original.level)
        }
    }
}
