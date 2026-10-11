package org.midorinext.android.ui.browser.menu

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class BrowserMenuSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun largeTextKeepsLibraryActionsReachableAndVpnRequiresCapability() {
        var vpnClicked = false
        var destination: NavDestination? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MidoriBrowserTheme(darkTheme = false) {
                    BrowserMenuSheet(onDismissRequest = {}) {
                        BrowserMenuMainContent(
                            showDefaultPrompt = true, vpnEnabled = false, extensionCount = 2,
                            onNavigate = { destination = it }, onVpnClick = { vpnClicked = true },
                            onAccountClick = {}, onSetDefault = {}, onDismissDefault = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("browser_menu_vpn").assertIsNotEnabled().performClick()
        assertFalse(vpnClicked)
        compose.onNodeWithTag("browser_menu_passwords").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(NavDestination.SavedPasswords, destination)
        compose.onNodeWithTag("browser_menu_settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun darkMenuUsesTheSameLibraryDestinationsAndClosesOnSelection() {
        var open by mutableStateOf(true)
        var destination: NavDestination? = null
        compose.setContent {
            MidoriBrowserTheme(darkTheme = true) {
                if (open) BrowserMenuSheet(onDismissRequest = { open = false }) { dismiss ->
                    BrowserMenuMainContent(
                        showDefaultPrompt = false, vpnEnabled = true, extensionCount = 0,
                        onNavigate = { destination = it; dismiss() }, onVpnClick = {},
                        onAccountClick = {}, onSetDefault = {}, onDismissDefault = {},
                    )
                }
            }
        }
        compose.onNodeWithTag("browser_menu_bookmarks").performClick()
        compose.waitForIdle()
        assertEquals(NavDestination.Bookmarks, destination)
        compose.onNodeWithTag("browser_menu_sheet").assertDoesNotExist()
    }
}
