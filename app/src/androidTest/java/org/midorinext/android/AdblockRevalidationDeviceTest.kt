package org.midorinext.android

import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
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

class AdblockRevalidationDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun fiftySpaCyclesChangeNetworkAndCosmeticsReversibly(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val store = app.store.get()
        withTimeout(30_000) { store.flow().first { it.restoreComplete } }
        val selected = store.state.selectedTabId
        val settings = app.adBlockSettings.get()
        val original = settings.current
        val feature = app.nativeContentBlockingFeature.get()
        val sources = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        val trackerSource = "https://midori-p12-trackers.invalid/filters"
        val adSource = "https://midori-p12-ads.invalid/filters"
        val phase = AtomicReference("{}")
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newFixedThreadPool(4)
        var tabId = ""
        var maxGap = 0.0
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
                            val body = when {
                                path.startsWith("/phase") -> phase.get()
                                path.startsWith("/probe-") -> "allowed"
                                else -> """<!doctype html><main id="article">Article</main><section id="spa"></section><script>
                                  let last=-1,previous=performance.now(),maxGap=0;
                                  setInterval(()=>{const now=performance.now();maxGap=Math.max(maxGap,now-previous);previous=now;},16);
                                  async function poll(){
                                    try {
                                      const phase=await fetch('/phase',{cache:'no-store'}).then(r=>r.json());
                                      if(phase.id!==undefined&&phase.id!==last){
                                        last=phase.id;
                                        history.pushState({},'', '/spa/'+last);
                                        document.querySelector('#spa').innerHTML='<div class="fixture-ad">Ad</div><div class="fixture-remove">Removable</div>';
                                        const blocked=await Promise.all(['tracker','ad'].map(kind=>fetch('/probe-'+kind+'?'+last,{cache:'no-store'}).then(()=>false,()=>true)));
                                        const deadline=performance.now()+10000;
                                        for(;;){
                                          const ad=document.querySelector('.fixture-ad'),removed=document.querySelector('.fixture-remove');
                                          const hidden=getComputedStyle(ad).display==='none';
                                          const removal=!removed||getComputedStyle(removed).display==='none';
                                          const full=phase.mode==='TRACKERS_AND_ADS';
                                          if(hidden===full&&removal===full){
                                            document.title=JSON.stringify({id:last,blocked,hidden,removal,
                                              content:getComputedStyle(document.querySelector('#article')).display!=='none',maxGap});
                                            break;
                                          }
                                          if(performance.now()>deadline)throw Error('cosmetics did not settle');
                                          await new Promise(resolve=>setTimeout(resolve,50));
                                        }
                                      }
                                    }catch(error){document.title=JSON.stringify({error:String(error),id:last});}
                                    setTimeout(poll,50);
                                  }
                                  poll();
                                </script>""".trimIndent()
                            }.toByteArray()
                            it.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: ${if (path.startsWith("/phase")) "application/json" else "text/html"}\r\nCache-Control: no-store\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body); flush()
                            }
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        var sequence = 0
        suspend fun apply(level: BlockingLevel, override: BlockingLevel? = null) {
            settings.setSiteLevel("127.0.0.1", override)
            settings.setLevel(level)
            sources.prepareEngines(settings.current)
            assertTrue("Policy $sequence applied", feature.awaitConfiguration())
            val effective = override ?: level
            val id = sequence++
            phase.set(JSONObject().put("id", id).put("mode", effective.name).toString())
            val state = withTimeout(20_000) { store.flow().first { state ->
                val title = state.tabs.find { it.id == tabId }?.content?.title.orEmpty()
                title.startsWith('{') && JSONObject(title).let { it.optInt("id", -1) == id }
            } }
            val result = JSONObject(state.tabs.first { it.id == tabId }.content.title)
            assertFalse(result.toString(), result.has("error"))
            assertEquals(result.toString(), effective != BlockingLevel.OFF, result.getJSONArray("blocked").getBoolean(0))
            assertEquals(result.toString(), effective == BlockingLevel.TRACKERS_AND_ADS, result.getJSONArray("blocked").getBoolean(1))
            assertTrue(result.toString(), result.getBoolean("content"))
            maxGap = maxOf(maxGap, result.getDouble("maxGap"))
            val expected = (if (settings.current.hasAdBlockingSites()) 1 else 0) +
                (if (level == BlockingLevel.TRACKERS || BlockingLevel.TRACKERS in settings.current.siteLevels.values) 1 else 0)
            assertEquals("Handles in phase $id", expected.toLong(), RustAdblock.liveEngineCount())
        }
        try {
            withTimeout(120_000) { sources.ready.await() }
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, false) }
            original.siteLevels.keys.forEach { settings.setSiteLevel(it, null) }
            settings.setLevel(BlockingLevel.TRACKERS_AND_ADS)
            val directory = File(app.filesDir, "adblock-sources").apply { mkdirs() }
            listOf(trackerSource to "/probe-tracker${'$'}xmlhttprequest\n",
                adSource to "/probe-ad${'$'}xmlhttprequest\n##.fixture-ad\n127.0.0.1##.fixture-remove:remove()\n").forEach { (url, rules) ->
                File(directory, "${RustFilterEngine.sha256(url.toByteArray())}.txt").writeText(rules)
            }
            assertTrue(settings.addSource(trackerSource, tracker = true))
            assertTrue(settings.addSource(adSource, tracker = false))
            settings.sourceUpdated(trackerSource, System.currentTimeMillis())
            settings.sourceUpdated(adSource, System.currentTimeMillis())
            sources.prepareEngines(settings.current)
            assertTrue(feature.awaitConfiguration())
            instrumentation.runOnMainSync {
                tabId = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/fixture", selectTab = true)
            }
            repeat(50) {
                apply(BlockingLevel.TRACKERS_AND_ADS)
                apply(BlockingLevel.TRACKERS_AND_ADS, BlockingLevel.TRACKERS)
                apply(BlockingLevel.TRACKERS)
                apply(BlockingLevel.OFF)
            }
            apply(BlockingLevel.TRACKERS_AND_ADS, BlockingLevel.OFF)
            apply(BlockingLevel.OFF, BlockingLevel.TRACKERS_AND_ADS)
            val metrics = "cycles=50 phases=$sequence maxGapMs=$maxGap handles=${RustAdblock.liveEngineCount()}"
            Log.i("AdblockRevalidation", metrics)
            instrumentation.sendStatus(2, android.os.Bundle().apply { putString("adblock_metric", metrics) })
        } finally {
            if (tabId.isNotEmpty()) store.dispatch(TabListAction.RemoveTabsAction(listOf(tabId)))
            selected?.let { store.dispatch(TabListAction.SelectTabAction(it)) }
            server.close(); executor.shutdownNow()
            sources.removeCustom(trackerSource); sources.removeCustom(adSource)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, true) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, true) }
            settings.setSiteLevel("127.0.0.1", null)
            original.siteLevels.forEach { (host, level) -> settings.setSiteLevel(host, level) }
            settings.setLevel(original.level)
            sources.prepareEngines(settings.current)
        }
    }
}
