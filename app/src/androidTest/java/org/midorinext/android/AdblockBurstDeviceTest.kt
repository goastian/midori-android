package org.midorinext.android

import android.util.Log
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

class AdblockBurstDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test
    fun networkAndDomBurstsDrainWithoutLosingProtection(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val store = app.store.get()
        withTimeout(30_000) { store.flow().first { it.restoreComplete } }
        val selected = store.state.selectedTabId
        val settings = app.adBlockSettings.get()
        val original = settings.current
        val feature = app.nativeContentBlockingFeature.get()
        val sources = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { sources.ready.await() }
        val putRules = sources.javaClass.getDeclaredMethod("putRules", String::class.java, NativeFilterRules::class.java).apply { isAccessible = true }
        val sourceUrl = "https://midori-burst-fixture.invalid/filters.txt"
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newFixedThreadPool(8)
        var tabId = ""
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
                            val body = if (path.startsWith("/frame")) "<!doctype html><p>Frame</p>" else """<!doctype html>
                                <div class="generic-midori-ad">Banner</div><main id="story">Article</main>
                                <script>
                                const fragment = document.createDocumentFragment();
                                for (let i=0;i<12000;i++) {
                                  const node=document.createElement('div'); node.className='payload';
                                  node.textContent=i%2 ? 'Article content' : 'Sponsored'; fragment.append(node);
                                }
                                document.body.append(fragment);
                                for (let i=0;i<4;i++) { const frame=document.createElement('iframe');frame.src='/frame?'+i;document.body.append(frame); }
                                let previous=performance.now(),maxGap=0,blocked=-1;
                                const heartbeat=setInterval(()=>{const now=performance.now();maxGap=Math.max(maxGap,now-previous);previous=now;},8);
                                const started=performance.now();
                                Promise.all(Array.from({length:1000},(_,i)=>fetch('/midori-burst-'+i).then(()=>0,()=>1)))
                                  .then(values=>{blocked=values.reduce((a,b)=>a+b,0);});
                                setTimeout(()=>{
                                  const added=document.createDocumentFragment();
                                  for(let i=0;i<1000;i++){const node=document.createElement('div');node.className='dynamic-midori-ad';added.append(node);}
                                  document.body.append(added);
                                },500);
                                const check=setInterval(()=>{
                                  const dynamic=document.querySelector('.dynamic-midori-ad');
                                  if(blocked<0||!dynamic)return;
                                  const result={blocked,generic:getComputedStyle(document.querySelector('.generic-midori-ad')).display==='none',
                                    procedural:getComputedStyle(document.querySelector('.payload')).display==='none',
                                    content:getComputedStyle(document.querySelectorAll('.payload')[1]).display!=='none',
                                    dynamic:getComputedStyle(dynamic).display==='none',maxGapMs:maxGap,elapsedMs:performance.now()-started};
                                  if(result.generic&&result.procedural&&result.dynamic){clearInterval(check);clearInterval(heartbeat);document.title=JSON.stringify(result);}
                                },100);
                                </script>""".trimIndent()
                            val bytes = body.toByteArray()
                            it.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(bytes); flush()
                            }
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        try {
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, false) }
            settings.setLevel(BlockingLevel.TRACKERS_AND_ADS)
            assertTrue(settings.addSource(sourceUrl, tracker = false))
            sources.prepareEngines(settings.current)
            putRules.invoke(sources, sourceUrl, parseHostSource("""
                /midori-burst-/${'$'}xmlhttprequest
                ##.generic-midori-ad
                ##.dynamic-midori-ad
                127.0.0.1##.payload:has-text(Sponsored)
            """.trimIndent()))
            assertTrue(feature.awaitConfiguration())
            instrumentation.runOnMainSync {
                tabId = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/fixture", selectTab = true)
            }
            val state = withTimeout(60_000) { store.flow().first { it.tabs.find { tab -> tab.id == tabId }?.content?.title?.startsWith('{') == true } }
            val result = JSONObject(state.tabs.first { it.id == tabId }.content.title)
            assertEquals(1000, result.getInt("blocked"))
            for (key in listOf("generic", "procedural", "content", "dynamic")) assertTrue("$key: $result", result.getBoolean(key))
            instrumentation.runOnMainSync {
                val work = feature.workState()
                assertEquals(0, work.pendingRequests)
                assertTrue(work.peakPendingRequests <= 128)
                assertEquals(0, work.dirtyPorts)
                val metrics = "result=$result work=$work"
                Log.i("AdblockBurst", metrics)
                instrumentation.sendStatus(2, android.os.Bundle().apply { putString("adblock_metric", metrics) })
            }
        } finally {
            if (tabId.isNotEmpty()) store.dispatch(TabListAction.RemoveTabsAction(listOf(tabId)))
            if (selected != null) store.dispatch(TabListAction.SelectTabAction(selected))
            server.close(); executor.shutdownNow()
            sources.removeCustom(sourceUrl)
            original.officialEnabled.forEach { settings.setOfficialEnabled(it, true) }
            original.sources.filter { it.enabled }.forEach { settings.setSourceEnabled(it.url, true) }
            settings.setLevel(original.level)
        }
    }
}
