package org.midorinext.android.ui.nav

import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.ext.navigateSingleTopTo
import org.midorinext.android.ui.browser.TabOpening

@RunWith(AndroidJUnit4::class)
class BrowserNavigationTest {
    @Test
    fun closeReturnsToExistingBrowserFromNestedSettingsWithoutReplacingItsArguments() {
        withController { controller ->
            controller.navigateSingleTopTo(NavDestination.Browser.route(TabOpening.PRIVATE))
            val browserEntry = controller.currentBackStackEntry!!
            controller.navigate(NavDestination.Preferences.route())
            controller.navigate(NavDestination.CustomizeSettings.route())

            controller.returnToBrowser()

            assertEquals(browserEntry.id, controller.currentBackStackEntry?.id)
            assertEquals(NavDestination.Browser.match, controller.currentDestination?.route)
            assertEquals(
                TabOpening.PRIVATE,
                @Suppress("DEPRECATION")
                controller.currentBackStackEntry?.arguments?.getSerializable("openNewTab"),
            )
            assertNull(controller.previousBackStackEntry)
        }
    }

    @Test
    fun closeRecoversBrowserWhenItsEntryIsMissing() {
        withController { controller ->
            val browserId = controller.currentDestination!!.id
            controller.navigate(NavDestination.Preferences.route()) {
                popUpTo(browserId) { inclusive = true }
            }

            controller.returnToBrowser()

            assertEquals(NavDestination.Browser.match, controller.currentDestination?.route)
        }
    }

    private fun withController(block: (TestNavHostController) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val controller = TestNavHostController(instrumentation.targetContext)
            controller.navigatorProvider.addNavigator(ComposeNavigator())
            controller.graph = controller.createGraph(startDestination = NavDestination.Browser.match) {
                composable(NavDestination.Browser.match, NavDestination.Browser.arguments) {}
                composable(NavDestination.Preferences.match) {}
                composable(NavDestination.CustomizeSettings.match) {}
            }
            block(controller)
        }
    }
}
