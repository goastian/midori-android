package org.midorinext.android

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.mozac.BrowserMemoryBudget
import org.midorinext.android.mozac.TabResourceMiddleware
import org.midorinext.android.widget.WidgetActivity
import org.midorinext.android.widget.WidgetViewModel
import org.mozilla.geckoview.GeckoSession

class MemorySurfaceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication

    @Test fun closingWidgetsUnregistersAndClosesTheirGeckoSessions() {
        val owners = app.memoryBudget.javaClass.getDeclaredField("owners").apply { isAccessible = true }
        repeat(10) {
            lateinit var model: WidgetViewModel
            lateinit var gecko: GeckoSession
            val scenario = ActivityScenario.launch(WidgetActivity::class.java)
            try {
                scenario.onActivity { activity ->
                    model = ViewModelProvider(activity)[WidgetViewModel::class.java]
                    gecko = model.session.javaClass.getDeclaredField("geckoSession").apply { isAccessible = true }.get(model.session) as GeckoSession
                    assertTrue(gecko.isOpen)
                    assertTrue((owners.get(app.memoryBudget) as Map<*, *>).containsKey(model))
                }
            } finally { scenario.close() }
            instrumentation.runOnMainSync {
                assertFalse(gecko.isOpen)
                assertFalse((owners.get(app.memoryBudget) as Map<*, *>).containsKey(model))
            }
        }
    }

    @Test fun twoRealBrowserStoresShareTheBudgetAndReleaseEveryOwnedSession() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val budget = BrowserMemoryBudget(instrumentation.targetContext)
        val resources = List(2) { TabResourceMiddleware(budget, scope) }
        val stores = resources.map { BrowserStore(middleware = listOf(it) + EngineMiddleware.create(app.engine.get(), trimMemoryAutomatically = false)) }
        val tabs = stores.map { TabsUseCases(it) }
        fun await(condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 60_000
            while (!condition()) {
                check(SystemClock.elapsedRealtime() < deadline) { "Cross-surface budget did not settle" }
                SystemClock.sleep(100)
            }
        }
        try {
            instrumentation.runOnMainSync {
                tabs.forEachIndexed { owner, useCases ->
                    repeat(3) { index -> useCases.addTab("data:text/html,<title>Surface $owner-$index</title><p>content</p>", private = true, selectTab = true) }
                }
            }
            await { stores.all { store -> store.state.tabs.size == 3 && store.state.tabs.all { !it.content.loading && it.content.title.startsWith("Surface ") } } }
            instrumentation.runOnMainSync { budget.onVisibilityChanged(false) }
            await { stores.sumOf { store -> store.state.tabs.count { it.engineState.engineSession != null } } <= 2 }
            assertEquals(6, stores.sumOf { it.state.tabs.size })
            assertTrue(stores.all { store -> store.state.tabs.first { it.id == store.state.selectedTabId }.engineState.engineSession != null })
        } finally {
            try {
                instrumentation.runOnMainSync { tabs.forEach { it.removeAllTabs() } }
                await { stores.all { it.state.tabs.isEmpty() } }
            } finally {
                instrumentation.runOnMainSync { resources.forEach { it.close() } }
                scope.cancel()
            }
        }
    }
}
