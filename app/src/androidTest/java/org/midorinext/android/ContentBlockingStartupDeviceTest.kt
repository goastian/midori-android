package org.midorinext.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.adblock.*

class ContentBlockingStartupDeviceTest {
    @Test fun disablingProtectionDrainsOnlyTheLatestPendingNavigation(): Unit = runBlocking {
        checkStartup(remove = false)
    }

    @Test fun closingATabDropsItsPendingStartupNavigation(): Unit = runBlocking {
        checkStartup(remove = true)
    }

    private suspend fun checkStartup(remove: Boolean) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = "p12-startup-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(preferences, mode)
        }
        val settings = AdBlockSettings(context)
        val startup = ContentBlockingStartup(settings)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val middleware = ContentBlockingStartupMiddleware(startup, scope)
        val observed = ConcurrentLinkedQueue<String>()
        val store = BrowserStore(middleware = listOf(middleware, { _, next, action ->
            if (action is EngineAction.LoadUrlAction) observed.add(action.url) else next(action)
            Unit
        }))
        try {
            val id = withContext(Dispatchers.Main) { TabsUseCases(store).addTab("https://fixture.invalid/", selectTab = true) }
            withTimeout(10_000) { store.flow().first { state -> state.tabs.any { it.id == id } } }
            withContext(Dispatchers.Main) {
                repeat(1000) { index -> middleware.invoke(store, { error("Navigation bypassed startup protection") },
                    EngineAction.LoadUrlAction(id, "https://fixture.invalid/$index")) }
            }
            assertTrue(observed.isEmpty())
            if (remove) {
                store.dispatch(TabListAction.RemoveTabsAction(listOf(id)))
                withTimeout(10_000) { store.flow().first { it.tabs.isEmpty() } }
            }
            settings.setLevel(BlockingLevel.OFF)
            if (remove) {
                delay(250)
                assertTrue(observed.isEmpty())
                assertTrue(store.state.tabs.isEmpty())
            } else {
                withTimeout(10_000) { while (observed.isEmpty()) delay(10) }
                assertEquals(listOf("https://fixture.invalid/999"), observed.toList())
            }
        } finally {
            scope.cancel()
            context.getSharedPreferences("native_adblock", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
