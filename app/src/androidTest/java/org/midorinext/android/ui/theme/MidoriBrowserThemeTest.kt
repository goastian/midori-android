package org.midorinext.android.ui.theme

import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MidoriBrowserThemeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun privacyChangesUseTheirFinalColorsInTheFirstFrameWithoutRecreatingContent() {
        var private by mutableStateOf(false)
        var renderedBackground = Color.Unspecified
        var renderedSurface = Color.Unspecified
        var renderedRaisedSurface = Color.Unspecified
        var renderedPrivate = false
        var contentIdentity: Any? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false, privacy = private, updateSystemBars = false) {
                val background = MaterialTheme.colorScheme.background
                val surface = MaterialTheme.colorScheme.surface
                val raisedSurface = MaterialTheme.colorScheme.surfaceContainerHigh
                val privacy = LocalMidoriTheme.current.private
                val identity = remember { Any() }
                SideEffect {
                    renderedBackground = background
                    renderedSurface = surface
                    renderedRaisedSurface = raisedSurface
                    renderedPrivate = privacy
                    contentIdentity = identity
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val originalContent = contentIdentity
        assertEquals(lightColorScheme.background, renderedBackground)
        assertEquals(lightColorScheme.surface, renderedSurface)
        assertEquals(lightColorScheme.surfaceContainerHigh, renderedRaisedSurface)

        compose.runOnIdle { private = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(renderedPrivate)
        assertEquals(privateColorScheme.background, renderedBackground)
        assertEquals(privateColorScheme.surface, renderedSurface)
        assertEquals(privateColorScheme.surfaceContainerHigh, renderedRaisedSurface)
        assertSame(originalContent, contentIdentity)

        compose.runOnIdle { private = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertFalse(renderedPrivate)
        assertEquals(lightColorScheme.background, renderedBackground)
        assertEquals(lightColorScheme.surface, renderedSurface)
        assertEquals(lightColorScheme.surfaceContainerHigh, renderedRaisedSurface)
        assertSame(originalContent, contentIdentity)
    }

    @Test
    fun outgoingNestedDestinationCannotChangeSystemBarsAfterLosingOwnership() {
        var trayIsActive by mutableStateOf(true)
        var trayIsPrivate by mutableStateOf(true)
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false, updateSystemBars = !trayIsActive) {
                MidoriBrowserTheme(
                    darkTheme = false,
                    privacy = trayIsPrivate,
                    updateSystemBars = trayIsActive,
                ) { }
            }
        }
        compose.runOnIdle {
            assertFalse(systemBarsUseDarkIcons())
            trayIsActive = false
        }
        compose.runOnIdle {
            assertTrue(systemBarsUseDarkIcons())
            trayIsPrivate = false
        }
        compose.runOnIdle { trayIsPrivate = true }
        compose.runOnIdle { assertTrue(systemBarsUseDarkIcons()) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun openSheetUpdatesItsOwnSystemBarsWithoutRecreatingContentOrChangingActivityBars() {
        var sheetIsVisible by mutableStateOf(true)
        var sheetIsDark by mutableStateOf(false)
        var sheetIsPrivate by mutableStateOf(false)
        var activityIsDark by mutableStateOf(false)
        var sheetWindow: Window? = null
        var contentIdentity: Any? = null
        compose.setContent {
            MidoriBrowserTheme(darkTheme = activityIsDark) {
                if (sheetIsVisible) {
                    MidoriBrowserTheme(
                        darkTheme = sheetIsDark,
                        privacy = sheetIsPrivate,
                        updateSystemBars = false,
                    ) {
                        ModalBottomSheet(
                            onDismissRequest = { sheetIsVisible = false },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        ) {
                            MidoriDialogSystemBars()
                            val view = LocalView.current
                            val identity = remember { Any() }
                            SideEffect {
                                sheetWindow = (view.parent as DialogWindowProvider).window
                                contentIdentity = identity
                            }
                            Box(Modifier.fillMaxWidth().height(240.dp))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val originalWindow = checkNotNull(sheetWindow)
        val originalContent = checkNotNull(contentIdentity)
        compose.runOnIdle {
            assertSystemBarIcons(originalWindow, useDarkIcons = true)
            assertSystemBarIcons(compose.activity.window, useDarkIcons = true)
            sheetIsDark = true
        }
        compose.runOnIdle {
            assertSystemBarIcons(originalWindow, useDarkIcons = false)
            assertSystemBarIcons(compose.activity.window, useDarkIcons = true)
            assertSame(originalWindow, sheetWindow)
            assertSame(originalContent, contentIdentity)
            sheetIsDark = false
        }
        compose.runOnIdle {
            assertSystemBarIcons(originalWindow, useDarkIcons = true)
            sheetIsPrivate = true
        }
        compose.runOnIdle {
            assertSystemBarIcons(originalWindow, useDarkIcons = false)
            assertSystemBarIcons(compose.activity.window, useDarkIcons = true)
            assertSame(originalWindow, sheetWindow)
            assertSame(originalContent, contentIdentity)
            sheetIsVisible = false
        }
        compose.runOnIdle {
            assertSystemBarIcons(compose.activity.window, useDarkIcons = true)
            activityIsDark = true
        }
        compose.runOnIdle {
            assertSystemBarIcons(compose.activity.window, useDarkIcons = false)
        }
    }

    private fun assertSystemBarIcons(window: Window, useDarkIcons: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals("Status bar icon appearance", useDarkIcons, controller.isAppearanceLightStatusBars)
        assertEquals("Navigation bar icon appearance", useDarkIcons, controller.isAppearanceLightNavigationBars)
    }

    private fun systemBarsUseDarkIcons(): Boolean {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        return controller.isAppearanceLightStatusBars && controller.isAppearanceLightNavigationBars
    }
}
