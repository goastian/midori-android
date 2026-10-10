package org.midorinext.android

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowserResourceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as MidoriApplication
    private val store get() = app.store.get()
    private val expectedLimit = InstrumentationRegistry.getArguments().getString("residentLimit", "5").toInt()
    private val samples = JSONArray()

    @Test
    fun privateFixturesKeepTheirStateWhileResidentSessionsStayBounded() {
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        await("session restoration") { store.state.restoreComplete && store.state.selectedTabId != null }
        await("initial document") {
            store.state.tabs.find { it.id == store.state.selectedTabId }?.let {
                it.engineState.engineSession != null && !it.content.loading && it.content.firstContentfulPaint
            } == true
        }
        SystemClock.sleep(3_000)
        val originalTabs = store.state.tabs
        val originalSelected = store.state.selectedTabId!!
        val fixtures = mutableListOf<String>()
        try {
            sample(0)
            for (number in 1..49) {
                addFixture(number, fixtures)
                if (number == 9 || number == 49) {
                    SystemClock.sleep(3_000)
                    if (expectedLimit > 0) await("resident budget") { residentCount() <= expectedLimit }
                    assertEquals(originalTabs.size + fixtures.size, store.state.tabs.size)
                    assertTrue(store.state.tabs.filter { it.id in fixtures }.all { it.content.private })
                    sample(number)
                }
            }
            if (expectedLimit > 0) {
                val suspended = store.state.tabs.first { it.id in fixtures && it.engineState.engineSession == null }
                assertNotNull(suspended.engineState.engineSessionState)
                val started = SystemClock.elapsedRealtime()
                store.dispatch(ContentAction.UpdateFirstContentfulPaintStateAction(suspended.id, false))
                store.dispatch(TabListAction.SelectTabAction(suspended.id))
                await("resuming suspended fixture") {
                    store.state.tabs.find { it.id == suspended.id }?.let {
                        it.engineState.engineSession != null && !it.content.loading &&
                            it.content.firstContentfulPaint && it.content.url == suspended.content.url
                    } == true
                }
                samples.put(JSONObject().put("resume_ms", SystemClock.elapsedRealtime() - started))
                instrumentation.runOnMainSync { app.sessionUseCases.get().loadUrl(fixtureUrl(1000)) }
                await("second history entry") {
                    store.state.tabs.find { it.id == suspended.id }?.let {
                        !it.content.loading && it.content.title == "Fixture 1000" && it.content.canGoBack
                    } == true
                }
                addFixture(50, fixtures)
                val lastFixture = addFixture(51, fixtures)
                store.dispatch(AppLifecycleAction.PauseAction)
                await("background resident budget") {
                    residentCount() <= 2 && store.state.tabs.find { it.id == suspended.id }?.engineState?.engineSession == null
                }
                assertNotNull(store.state.tabs.find { it.id == lastFixture }?.engineState?.engineSession)
                samples.put(JSONObject().put("background_resident_sessions", residentCount()))
                store.dispatch(AppLifecycleAction.ResumeAction)
                store.dispatch(ContentAction.UpdateFirstContentfulPaintStateAction(suspended.id, false))
                store.dispatch(TabListAction.SelectTabAction(suspended.id))
                await("history restored with a fresh paint") {
                    store.state.tabs.find { it.id == suspended.id }?.let {
                        it.engineState.engineSession != null && !it.content.loading &&
                            it.content.firstContentfulPaint && it.content.canGoBack && it.content.title == "Fixture 1000"
                    } == true
                }
                instrumentation.runOnMainSync { app.sessionUseCases.get().goBack(suspended.id) }
                await("back navigation after suspension") {
                    store.state.tabs.find { it.id == suspended.id }?.let {
                        !it.content.loading && it.content.title == suspended.content.title && it.content.url == suspended.content.url
                    } == true
                }
                samples.put(JSONObject().put("history_restored", true))
            }
        } finally {
            store.dispatch(TabListAction.RemoveTabsAction(fixtures))
            store.dispatch(TabListAction.SelectTabAction(originalSelected))
            await("fixture cleanup") { store.state.tabs.none { it.id in fixtures } && store.state.selectedTabId == originalSelected }
            assertEquals(originalTabs.map { it.id }, store.state.tabs.map { it.id })
            File(app.cacheDir, "browser-resource-results.json").writeText(samples.toString(2))
        }
    }

    private fun addFixture(number: Int, fixtures: MutableList<String>): String {
        var id = ""
        instrumentation.runOnMainSync {
            id = app.tabsUseCases.get().addTab(url = fixtureUrl(number), private = true, selectTab = true)
        }
        fixtures.add(id)
        await("fixture $number") {
            store.state.tabs.find { it.id == id }?.let {
                it.engineState.engineSession != null && !it.content.loading && it.content.title == "Fixture $number"
            } == true
        }
        return id
    }

    private fun fixtureUrl(number: Int) = "data:text/html,<html><head><title>Fixture $number</title></head>" +
        "<body><h1>Fixture $number</h1><canvas id='c' width='1200' height='1200'></canvas>" +
        "<script>let x=document.getElementById('c').getContext('2d');x.fillStyle='green';x.fillRect(0,0,1200,1200);</script></body></html>"

    private fun sample(fixtureTabs: Int) {
        val memory = Debug.MemoryInfo()
        Debug.getMemoryInfo(memory)
        samples.put(JSONObject().put("scenario_tabs", store.state.tabs.size).put("fixture_tabs", fixtureTabs)
            .put("resident_sessions", residentCount())
            .put("main_process_pss_kb", memory.totalPss))
    }

    private fun residentCount() = store.state.tabs.count { it.engineState.engineSession != null }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for $description; resident=${residentCount()}" }
            SystemClock.sleep(100)
        }
    }
}
