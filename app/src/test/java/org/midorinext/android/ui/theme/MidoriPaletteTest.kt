package org.midorinext.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidoriPaletteTest {
    private val schemes = mapOf(
        "light" to lightColorScheme,
        "dark" to darkColorScheme,
        "private" to privateColorScheme,
    )

    @Test
    fun textAndActionsRemainLegibleInEveryMode() {
        for ((name, colors) in schemes) {
            val textPairs = listOf(
                "background" to (colors.onBackground to colors.background),
                "surface" to (colors.onSurface to colors.surface),
                "surface variant" to (colors.onSurfaceVariant to colors.surfaceVariant),
                "primary" to (colors.onPrimary to colors.primary),
                "primary container" to (colors.onPrimaryContainer to colors.primaryContainer),
                "secondary" to (colors.onSecondary to colors.secondary),
                "secondary container" to (colors.onSecondaryContainer to colors.secondaryContainer),
                "tertiary" to (colors.onTertiary to colors.tertiary),
                "tertiary container" to (colors.onTertiaryContainer to colors.tertiaryContainer),
                "error" to (colors.onError to colors.error),
                "error container" to (colors.onErrorContainer to colors.errorContainer),
                "inverse" to (colors.inverseOnSurface to colors.inverseSurface),
                "primary action" to (colors.primary to colors.surface),
                "error message" to (colors.error to colors.surface),
            )
            for ((role, pair) in textPairs) {
                assertContrast("$name $role", pair.first, pair.second, 4.5f)
            }

            val surfaces = listOf(
                colors.surfaceContainerLowest,
                colors.surfaceContainerLow,
                colors.surfaceContainer,
                colors.surfaceContainerHigh,
                colors.surfaceContainerHighest,
            )
            for (surface in surfaces) {
                assertContrast("$name container text", colors.onSurface, surface, 4.5f)
                assertContrast("$name container supporting text", colors.onSurfaceVariant, surface, 4.5f)
                assertContrast("$name control outline", colors.outline, surface, 3f)
            }
        }
    }

    @Test
    fun surfacesHaveDistinctLayersAndPrivateModeHasItsOwnBase() {
        for ((name, colors) in schemes) {
            assertNotEquals("$name background must not alias surface", colors.background, colors.surface)
            assertNotEquals("$name raised surface must differ", colors.surface, colors.surfaceContainerHigh)
            assertNotEquals("$name quiet borders must differ from controls", colors.outline, colors.outlineVariant)
            assertTrue("$name containers must be opaque", colors.secondaryContainer.alpha == 1f)
        }
        assertNotEquals(darkColorScheme.background, privateColorScheme.background)
        assertNotEquals(darkColorScheme.surface, privateColorScheme.surface)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color, minimum: Float) {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
        assertTrue("$name contrast was $ratio; expected at least $minimum", ratio >= minimum)
    }
}
