package org.midorinext.android

import android.content.ComponentCallbacks2
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.mozac.BrowserMemoryBudget
import org.midorinext.android.mozac.ResidentTab

class MemoryBudgetDeviceTest {
    @Test fun allSurfacesShareOneBudgetAndProtectedSessionsRemainSafe() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val budget = BrowserMemoryBudget(instrumentation.targetContext)
            instrumentation.sendStatus(0, Bundle().apply {
                putString("memoryProfile", budget.profile.name)
                putInt("foregroundLimit", budget.profile.foreground)
                putInt("backgroundLimit", budget.profile.background)
            })
            val browser = Any()
            val pwa = Any()
            val widget = Any()
            budget.register(browser) {}
            budget.register(pwa) {}
            budget.register(widget) {}
            budget.update(browser, listOf(ResidentTab("browser-selected", 10, true), ResidentTab("browser-idle", 1, false)))
            budget.update(pwa, listOf(ResidentTab("pwa-selected", 20, true), ResidentTab("pwa-idle", 2, false)))
            budget.update(widget, listOf(ResidentTab("widget", 30, true)))
            budget.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            assertEquals(listOf("browser-idle"), budget.candidates(browser))
            assertEquals(listOf("pwa-idle"), budget.candidates(pwa))
            assertTrue(budget.candidates(widget).isEmpty())
            budget.unregister(widget)
            budget.unregister(pwa)
            budget.onVisibilityChanged(true)
            assertTrue(budget.candidates(browser).isEmpty())
            budget.unregister(browser)
        }
    }

    @Test fun visibilityAndTrimOnlyTouchRegisteredResourcesWithoutConstructingLazyResources() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val budget = BrowserMemoryBudget(instrumentation.targetContext)
            val favicons = org.midorinext.android.newtab.HomeFaviconRepository(
                instrumentation.targetContext, dagger.Lazy { error("Cleanup initialized the icon client") }, budget,
            )
            val loader = favicons.javaClass.getDeclaredField("iconLoader").apply { isAccessible = true }.get(favicons) as Lazy<*>
            assertFalse(loader.isInitialized())
            val levels = mutableListOf<Int>()
            budget.registerCache(levels) { cache, level -> cache.add(level) }
            budget.onVisibilityChanged(false)
            budget.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            budget.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
            assertFalse(loader.isInitialized())
            assertEquals(3, levels.size)
            assertTrue(levels.all { it == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL })
        }
    }
}
