package org.midorinext.android.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
        var renderedPrivate = false
        var contentIdentity: Any? = null
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false, privacy = private, updateSystemBars = false) {
                val background = MaterialTheme.colorScheme.background
                val privacy = LocalMidoriTheme.current.private
                val identity = remember { Any() }
                SideEffect {
                    renderedBackground = background
                    renderedPrivate = privacy
                    contentIdentity = identity
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val originalContent = contentIdentity
        assertEquals(lightColorScheme.primaryContainer, renderedBackground)

        compose.runOnIdle { private = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertTrue(renderedPrivate)
        assertEquals(PrivateSurfaceDark, renderedBackground)
        assertSame(originalContent, contentIdentity)

        compose.runOnIdle { private = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertFalse(renderedPrivate)
        assertEquals(lightColorScheme.primaryContainer, renderedBackground)
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

    private fun systemBarsUseDarkIcons(): Boolean {
        val window = compose.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        return controller.isAppearanceLightStatusBars && controller.isAppearanceLightNavigationBars
    }
}
