package org.midorinext.android

import androidx.activity.ComponentActivity
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.adblock.*
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors

class ScriptletsDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication

    @Test
    fun geckoRunsScriptletsBeforePageCodeUnderCspInNormalAndPrivateSessionsAndHonorsOptOut(): Unit = runBlocking {
        val settings = app.adBlockSettings.get()
        val original = settings.current
        val feature = app.nativeContentBlockingFeature.get()
        val sourceField = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }
        val sources = sourceField.get(feature) as NativeFilterSources
        val putRules = sources.javaClass.getDeclaredMethod("putRules", String::class.java, NativeFilterRules::class.java).apply { isAccessible = true }
        val sourceUrl = "https://midori-scriptlet-fixture.invalid/filters.txt"
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newCachedThreadPool()
        val sessions = mutableListOf<GeckoSession>()
        val json = """{"playerAds":[{"id":"ad"}],"content":{"title":"Movie","stream":"main.m3u8"}}"""
        executor.submit {
            while (!server.isClosed) {
                try {
                    val socket = server.accept()
                    executor.submit {
                        socket.use {
                            it.soTimeout = 5_000
                            val reader = it.getInputStream().bufferedReader()
                            val path = reader.readLine().split(' ')[1]
                            while (!reader.readLine().isNullOrEmpty()) { }
                            val player = path.startsWith("/player")
                            val body = if (player) json else """<!doctype html><script nonce="midori">
                                window.midoriUboAds = true;
                                window.midoriAdguardAds = true;
                                const parsed = JSON.parse('$json');
                                fetch('/player').then(response => response.json()).then(fetched => {
                                  document.title = JSON.stringify({ubo:midoriUboAds,adguard:midoriAdguardAds,storage:localStorage.getItem('midori-scriptlet'),parsed,fetched});
                                }).catch(error => {document.title = 'error:' + error;});
                            </script>"""
                            val bytes = body.toByteArray()
                            it.getOutputStream().apply {
                                write(("HTTP/1.1 200 OK\r\nContent-Type: ${if (player) "application/json" else "text/html"}\r\n" +
                                    "Content-Security-Policy: script-src 'nonce-midori'; connect-src 'self'\r\n" +
                                    "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                                write(bytes)
                                flush()
                            }
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        suspend fun load(private: Boolean): JSONObject {
            Log.i("ScriptletsDeviceTest", "Loading test page, private=$private")
            val title = MutableStateFlow("")
            instrumentation.runOnMainSync {
                val session = GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(private).build())
                session.contentDelegate = object : GeckoSession.ContentDelegate {
                    override fun onTitleChange(session: GeckoSession, value: String?) { title.value = value.orEmpty() }
                }
                session.open(app.geckoRuntime.get())
                val view = GeckoView(activity.activity)
                view.setSession(session)
                activity.activity.setContentView(view)
                sessions += session
                session.loadUri("http://127.0.0.1:${server.localPort}/fixture?private=$private")
            }
            val result = withTimeout(30_000) { title.first { it.startsWith('{') || it.startsWith("error:") } }
            assertFalse(result, result.startsWith("error:"))
            return JSONObject(result)
        }
        try {
            settings.setLevel(BlockingLevel.OFF)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, false) }
            Log.i("ScriptletsDeviceTest", "Waiting for filter sources")
            withTimeout(30_000) { sources.ready.await() }
            Log.i("ScriptletsDeviceTest", "Filter sources ready")
            assertTrue(settings.addSource(sourceUrl, tracker = false))
            val text = """
                127.0.0.1##+js(set, midoriUboAds, false)
                127.0.0.1#%#//scriptlet('set-constant', 'midoriAdguardAds', 'false')
                127.0.0.1##+js(json-prune, playerAds)
                127.0.0.1##+js(json-prune-fetch-response, playerAds, , propsToMatch, url:player)
                127.0.0.1##+js(set-local-storage-item, midori-scriptlet, true)
            """.trimIndent()
            putRules.invoke(sources, sourceUrl, parseHostSource(text))
            settings.setSiteLevel("127.0.0.1", BlockingLevel.TRACKERS_AND_ADS)
            Log.i("ScriptletsDeviceTest", "Waiting for scriptlet bridge")
            withTimeout(30_000) { feature.ready.await() }
            Log.i("ScriptletsDeviceTest", "Scriptlet bridge ready")
            assertTrue("Scriptlet policy was not acknowledged", feature.awaitConfiguration())
            for (private in listOf(false, true)) {
                val result = load(private)
                assertFalse("uBO page-world scriptlet, private=$private: $result", result.getBoolean("ubo"))
                assertFalse("AdGuard page-world scriptlet, private=$private: $result", result.getBoolean("adguard"))
                assertEquals("Isolated-world scriptlet, private=$private", "true", result.getString("storage"))
                for (field in listOf("parsed", "fetched")) {
                    assertFalse("Video ad data: $result", result.getJSONObject(field).has("playerAds"))
                    assertEquals("main.m3u8", result.getJSONObject(field).getJSONObject("content").getString("stream"))
                }
            }
            settings.setSiteLevel("127.0.0.1", BlockingLevel.OFF)
            assertTrue(feature.awaitConfiguration())
            val disabled = load(false)
            assertTrue(disabled.getBoolean("ubo"))
            assertTrue(disabled.getBoolean("adguard"))
            assertTrue(disabled.getJSONObject("parsed").has("playerAds"))
            assertTrue(disabled.getJSONObject("fetched").has("playerAds"))
        } finally {
            instrumentation.runOnMainSync { sessions.forEach(GeckoSession::close) }
            server.close()
            executor.shutdownNow()
            sources.removeCustom(sourceUrl)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, true) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, true) }
            settings.setSiteLevel("127.0.0.1", original.siteLevels["127.0.0.1"])
            settings.setLevel(original.level)
        }
    }
}
