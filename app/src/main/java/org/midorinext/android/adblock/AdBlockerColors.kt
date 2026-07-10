package org.midorinext.android.adblock

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.midorinext.android.ui.theme.animateColor

internal data class AdBlockerColors(
    val border: Color,
    val content: Color,
    val toggleThumb: Color,
    val toggleBorder: Color,
    val toggleTrack: Color
)

@Composable
internal fun AdBlockerColors.animatedColors() = copy(
    border = animateColor(border),
    content = animateColor(content),
    toggleThumb = animateColor(toggleThumb),
    toggleBorder = animateColor(toggleBorder),
    toggleTrack = animateColor(toggleTrack)
)

internal val enabledLight = AdBlockerColors(
    border = Color(0xFF02A262),
    content = Color(0xFFEDFDF5),
    toggleThumb = Color(0xFFF9FAFB),
    toggleBorder = Color(0xFF008055),
    toggleTrack = Color(0xFF008055)
)

internal val disabledLight = AdBlockerColors(
    border = Color(0xFF212327),
    content = Color(0xFFF2F4F7),
    toggleThumb = Color(0xFF282B2F),
    toggleBorder = Color(0xFF282B2F),
    toggleTrack = Color(0x145D7598)
)

internal val enabledDark = AdBlockerColors(
    border = Color(0xFF36BF7F),
    content = Color(0xFF00422C),
    toggleThumb = Color(0xFF282B2F),
    toggleBorder = Color(0xFF70D7A7),
    toggleTrack = Color(0xFF70D7A7)
)

internal val disabledDark = AdBlockerColors(
    border = Color(0xFFFFFFFF),
    content = Color(0xFF131416),
    toggleThumb = Color(0xFFF9FAFB),
    toggleBorder = Color(0xFFF9FAFB),
    toggleTrack = Color(0x14C8DCF9)
)
