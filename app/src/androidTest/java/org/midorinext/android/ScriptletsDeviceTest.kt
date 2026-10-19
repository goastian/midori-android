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
                            val clean = path.startsWith("/clean")
                            val replacement = path.startsWith("/replacement")
                            val image = path.startsWith("/csp-image")
                            val body = when {
                                player -> json
                                clean -> JSONObject().put("path", path).toString()
                                replacement -> "window.originalReplacementExecuted = true;"
                                else -> """<!doctype html>
                                <script type="application/json" nonce="midori" id="midoriRegexPlain">midoriRegexCandidate: example extra text</script>
                                <script type="application/json" nonce="midori" id="midoriRegexEscaped">midoriRegexCandidate: \x61</script>
                                <script nonce="midori">
                                window.midoriUboAds = true;
                                window.midoriAdguardAds = true;
                                const parsed = JSON.parse('$json');
                                const replacement = new Promise(resolve => {
                                  const script = document.createElement('script'); script.nonce = 'midori'; script.src = '/replacement.js';
                                  script.onload = () => resolve({loaded:true,original:window.originalReplacementExecuted === true});
                                  script.onerror = () => resolve({loaded:false,original:window.originalReplacementExecuted === true});
                                  document.documentElement.append(script);
                                });
                                const image = new Promise(resolve => {
                                  const image = new Image(); image.onload = () => resolve(false); image.onerror = () => resolve(true); image.src = '/csp-image.gif';
                                });
                                Promise.all([
                                  fetch('/player').then(response => response.json()),
                                  fetch('/ads42.js').then(() => false).catch(() => true),
                                  fetch('/clean?id=42&utm_source=advert').then(response => response.json()),
                                  replacement, image
                                ]).then(([fetched,regexBlocked,clean,replacement,imageBlocked]) => {
                                  const regexPlain = document.getElementById('midoriRegexPlain').textContent.includes('example extra text');
                                  const regexEscaped = document.getElementById('midoriRegexEscaped').textContent.includes(String.fromCharCode(92) + 'x');
                                  document.title = JSON.stringify({ubo:midoriUboAds,adguard:midoriAdguardAds,storage:localStorage.getItem('midori-scriptlet'),parsed,fetched,regexBlocked,clean,replacement,imageBlocked,regexPlain,regexEscaped});
                                }).catch(error => {document.title = 'error:' + error;});
                            </script>"""
                            }
                            val bytes = if (image) android.util.Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT) else body.toByteArray()
                            val contentType = when { player || clean -> "application/json"; replacement -> "application/javascript"; image -> "image/gif"; else -> "text/html" }
                            it.getOutputStream().apply {
                                write(("HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n" +
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
                127.0.0.1##+js(rmnt, script, /\\x/, excludes, document.title)
                /ads[0-9]+\.js/${'$'}xmlhttprequest
                /replacement.js${'$'}script,redirect=noopjs
                ||127.0.0.1^${'$'}removeparam=utm_source
                ||127.0.0.1^${'$'}csp=img-src 'none'
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
                assertTrue("Native regex block, private=$private: $result", result.getBoolean("regexBlocked"))
                assertTrue("Replacement script loaded: $result", result.getJSONObject("replacement").getBoolean("loaded"))
                assertFalse("Original advertising script executed: $result", result.getJSONObject("replacement").getBoolean("original"))
                assertEquals("/clean?id=42", result.getJSONObject("clean").getString("path"))
                assertTrue("CSP response filter, private=$private: $result", result.getBoolean("imageBlocked"))
                assertTrue("Ordinary script text must keep literal x characters: $result", result.getBoolean("regexPlain"))
                assertFalse("Escaped obfuscation pattern must still match: $result", result.getBoolean("regexEscaped"))
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
            assertFalse(disabled.getBoolean("regexBlocked"))
            assertTrue(disabled.getJSONObject("replacement").getBoolean("original"))
            assertEquals("/clean?id=42&utm_source=advert", disabled.getJSONObject("clean").getString("path"))
            assertFalse(disabled.getBoolean("imageBlocked"))
            assertTrue(disabled.getBoolean("regexPlain"))
            assertTrue(disabled.getBoolean("regexEscaped"))
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
