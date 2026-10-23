package org.midorinext.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import org.midorinext.android.R
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.animation.MidoriMotion
import org.midorinext.android.ui.animation.systemReduceMotionRequested

data class MidoriIcons(
    @DrawableRes val zap: Int,
    @DrawableRes val zapAnimated: Int
)

private val lightIcons = MidoriIcons(
    zap = R.drawable.icons_zap,
    zapAnimated = R.drawable.animated_zap
)

private val darkAndPrivateIcons = MidoriIcons(
    zap = R.drawable.icons_zap_night,
    zapAnimated = R.drawable.animated_zap_dark
)

data class MidoriTheme(val dark: Boolean, val private: Boolean, val icons: MidoriIcons)
val LocalMidoriTheme = compositionLocalOf { MidoriTheme(dark = false, private = false, icons = lightIcons) }

val lightColorScheme = lightColorScheme(
    primary = ActionPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = SurfaceLight,
    onPrimaryContainer = TextPrimaryLight,
    inversePrimary = ActionPrimaryDark,
    secondary = TextSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = SurfaceSecondaryLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = TextPrimaryLight,
    onTertiary = SurfaceLight,
    tertiaryContainer = SurfaceLight,
    onTertiaryContainer = TextPrimaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceTint = ActionPrimaryLight,
    inverseSurface = SurfaceRaisedDark,
    inverseOnSurface = TextPrimaryDark,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorSurfaceLight,
    onErrorContainer = Color(0xFF410E0B),
    outline = BorderLight,
    outlineVariant = BorderSubtleLight,
    scrim = ScrimDark,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDCE3D8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F3EC),
    surfaceContainer = SurfaceSecondaryLight,
    surfaceContainerHigh = Color(0xFFE4EADF),
    surfaceContainerHighest = SurfaceMutedLight,
    primaryFixed = Color(0xFFBCECCE),
    primaryFixedDim = ActionPrimaryDark,
    onPrimaryFixed = Color(0xFF002113),
    onPrimaryFixedVariant = Color(0xFF005231),
    secondaryFixed = Color(0xFFD7E8D8),
    secondaryFixedDim = Color(0xFFBBCDBD),
    onSecondaryFixed = Color(0xFF112117),
    onSecondaryFixedVariant = Color(0xFF3D5143),
    tertiaryFixed = Color(0xFFDEE4D6),
    tertiaryFixedDim = Color(0xFFC2C9BA),
    onTertiaryFixed = Color(0xFF1D251B),
    onTertiaryFixedVariant = Color(0xFF424B3E),
)

val darkColorScheme = darkColorScheme(
    primary = ActionPrimaryDark,
    onPrimary = Color(0xFF003821),
    primaryContainer = SurfaceDark,
    onPrimaryContainer = TextPrimaryDark,
    inversePrimary = ActionPrimaryLight,
    secondary = TextSecondaryDark,
    onSecondary = TextPrimaryLight,
    secondaryContainer = SurfaceSecondaryDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = TextPrimaryDark,
    onTertiary = SurfaceDark,
    tertiaryContainer = SurfaceRaisedDark,
    onTertiaryContainer = TextPrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceMutedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceTint = ActionPrimaryDark,
    inverseSurface = SurfaceSecondaryLight,
    inverseOnSurface = TextPrimaryLight,
    error = ErrorDark,
    onError = Color(0xFF601410),
    errorContainer = ErrorSurfaceDark,
    onErrorContainer = ErrorSurfaceLight,
    outline = BorderDark,
    outlineVariant = BorderSubtleDark,
    scrim = ScrimDark,
    surfaceBright = Color(0xFF39463D),
    surfaceDim = BackgroundDark,
    surfaceContainerLowest = Color(0xFF0B100D),
    surfaceContainerLow = Color(0xFF1D2520),
    surfaceContainer = SurfaceSecondaryDark,
    surfaceContainerHigh = SurfaceRaisedDark,
    surfaceContainerHighest = SurfaceMutedDark,
    primaryFixed = lightColorScheme.primaryFixed,
    primaryFixedDim = lightColorScheme.primaryFixedDim,
    onPrimaryFixed = lightColorScheme.onPrimaryFixed,
    onPrimaryFixedVariant = lightColorScheme.onPrimaryFixedVariant,
    secondaryFixed = lightColorScheme.secondaryFixed,
    secondaryFixedDim = lightColorScheme.secondaryFixedDim,
    onSecondaryFixed = lightColorScheme.onSecondaryFixed,
    onSecondaryFixedVariant = lightColorScheme.onSecondaryFixedVariant,
    tertiaryFixed = lightColorScheme.tertiaryFixed,
    tertiaryFixedDim = lightColorScheme.tertiaryFixedDim,
    onTertiaryFixed = lightColorScheme.onTertiaryFixed,
    onTertiaryFixedVariant = lightColorScheme.onTertiaryFixedVariant,
)

internal val privateColorScheme = darkColorScheme.copy(
    primary = PrivateAction,
    onPrimary = Color(0xFF073B28),
    primaryContainer = PrivateSurfaceDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = Color(0xFFB5D1C1),
    onSecondary = PrivateBackground,
    secondaryContainer = Color(0xFF1B3B2F),
    onSecondaryContainer = TextPrimaryDark,
    tertiary = TextPrimaryDark,
    onTertiary = PrivateBackground,
    tertiaryContainer = PrivateAccent,
    onTertiaryContainer = TextPrimaryDark,
    background = PrivateBackground,
    onBackground = TextPrimaryDark,
    surface = PrivateSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF2D5142),
    onSurfaceVariant = Color(0xFFB5D1C1),
    surfaceTint = PrivateAction,
    outline = Color(0xFF8BA697),
    outlineVariant = Color(0xFF385849),
    surfaceBright = Color(0xFF345C4B),
    surfaceDim = PrivateBackground,
    surfaceContainerLowest = Color(0xFF071A14),
    surfaceContainerLow = Color(0xFF143127),
    surfaceContainer = Color(0xFF1B3B2F),
    surfaceContainerHigh = PrivateAccent,
    surfaceContainerHighest = Color(0xFF2D5142),
)

private val MidoriShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun animateColor(targetValue: Color, reduceMotion: Boolean) =
    animateColorAsState(
        targetValue = targetValue,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = MidoriMotion.SurfaceDurationMillis),
        label = "theme colors"
    ).value

@Composable
fun ColorScheme.animatedColors(reduceMotion: Boolean) = copy(
    primary = animateColor(primary, reduceMotion),
    onPrimary = animateColor(onPrimary, reduceMotion),
    primaryContainer = animateColor(primaryContainer, reduceMotion),
    onPrimaryContainer = animateColor(onPrimaryContainer, reduceMotion),
    inversePrimary = animateColor(inversePrimary, reduceMotion),
    secondary = animateColor(secondary, reduceMotion),
    onSecondary = animateColor(onSecondary, reduceMotion),
    secondaryContainer = animateColor(secondaryContainer, reduceMotion),
    onSecondaryContainer = animateColor(onSecondaryContainer, reduceMotion),
    tertiary = animateColor(tertiary, reduceMotion),
    onTertiary = animateColor(onTertiary, reduceMotion),
    tertiaryContainer = animateColor(tertiaryContainer, reduceMotion),
    onTertiaryContainer = animateColor(onTertiaryContainer, reduceMotion),
    outline = animateColor(outline, reduceMotion),
    outlineVariant = animateColor(outlineVariant, reduceMotion),
    surface = animateColor(surface, reduceMotion),
    onSurface = animateColor(onSurface, reduceMotion),
    surfaceVariant = animateColor(surfaceVariant, reduceMotion),
    onSurfaceVariant = animateColor(onSurfaceVariant, reduceMotion),
    surfaceTint = animateColor(surfaceTint, reduceMotion),
    inverseSurface = animateColor(inverseSurface, reduceMotion),
    inverseOnSurface = animateColor(inverseOnSurface, reduceMotion),
    background = animateColor(background, reduceMotion),
    onBackground = animateColor(onBackground, reduceMotion),
    error = animateColor(error, reduceMotion),
    onError = animateColor(onError, reduceMotion),
    errorContainer = animateColor(errorContainer, reduceMotion),
    onErrorContainer = animateColor(onErrorContainer, reduceMotion),
    scrim = animateColor(scrim, reduceMotion),
    surfaceBright = animateColor(surfaceBright, reduceMotion),
    surfaceDim = animateColor(surfaceDim, reduceMotion),
    surfaceContainerLowest = animateColor(surfaceContainerLowest, reduceMotion),
    surfaceContainerLow = animateColor(surfaceContainerLow, reduceMotion),
    surfaceContainer = animateColor(surfaceContainer, reduceMotion),
    surfaceContainerHigh = animateColor(surfaceContainerHigh, reduceMotion),
    surfaceContainerHighest = animateColor(surfaceContainerHighest, reduceMotion),
)

@Composable
fun MidoriBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    privacy: Boolean = false,
    updateSystemBars: Boolean = true,
    content: @Composable () -> Unit
) {
    val reduceMotion = systemReduceMotionRequested()
    val targetColors = when {
        privacy -> privateColorScheme
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }
    val colorScheme = key(privacy) { targetColors.animatedColors(reduceMotion) }

    val icons = if (privacy || darkTheme) darkAndPrivateIcons else lightIcons

    // TODO: This prevents previews from rendering with the qwant theme. Maybe this could be extracted in its own component.
    val view = LocalView.current
    if (updateSystemBars && !view.isInEditMode) {
        val window = (view.context as Activity).window
        SideEffect {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.surface.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.surface.toArgb()
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !(darkTheme || privacy)
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !(darkTheme || privacy)
        }
    }

    CompositionLocalProvider(
        LocalMidoriTheme provides MidoriTheme(darkTheme, privacy, icons),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MidoriShapes,
            typography = Typography,
            content = content
        )
    }
}
