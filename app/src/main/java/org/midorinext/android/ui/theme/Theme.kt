package org.midorinext.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
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
    primary = ActionBlue400,
    onPrimary = Color.White,
    tertiary = PrimaryDarkGrey,
    onTertiary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Grey900,
    secondaryContainer = BackgroundSecondaryLightGrey,
    onSecondaryContainer = PrimaryDarkGrey,
    tertiaryContainer = Color.White,
    onTertiaryContainer = Grey900,
    outline = BorderLightGrey,
    surfaceVariant = Grey100,
    onSurfaceVariant = Grey900,
    error = Red
)

val darkColorScheme = darkColorScheme(
    primary = ActionBlue200,
    onPrimary = Grey900,
    tertiary = Color.White,
    onTertiary = PrimaryDarkGrey,
    primaryContainer = Grey750,
    onPrimaryContainer = Color.White,
    secondaryContainer = BackgroundSecondaryDarkGrey,
    onSecondaryContainer = Color.White,
    tertiaryContainer = Grey700,
    onTertiaryContainer = Color.White,
    outline = BorderDarkGrey,
    surfaceVariant = Grey600,
    onSurfaceVariant = Color.White,
    error = RedLight
)

private val privateColorScheme = darkColorScheme.copy(
    primary = Purple200,
    onPrimary = Grey900,
    tertiary = Color.White,
    onTertiary = PrimaryDarkGrey,
    primaryContainer = Purple700,
    onPrimaryContainer = Color.White,
    secondaryContainer = Grey000Alpha16,
    onSecondaryContainer = Color.White,
    tertiaryContainer = PurpleTertiary,
    onTertiaryContainer = Grey900,
    outline = Grey000Alpha16,
    surfaceVariant = Grey000Alpha16,
    onSurfaceVariant = Color.White,
    error = RedLight
)

@Composable
fun animateColor(targetValue: Color) =
    animateColorAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 250),
        label = "theme colors"
    ).value

@Composable
fun ColorScheme.animatedColors() = copy(
    primary = animateColor(primary),
    onPrimary = animateColor(onPrimary),
    primaryContainer = animateColor(primaryContainer),
    onPrimaryContainer = animateColor(onPrimaryContainer),
    secondaryContainer = animateColor(secondaryContainer),
    onSecondaryContainer = animateColor(onSecondaryContainer),
    tertiary = animateColor(tertiary),
    tertiaryContainer = animateColor(tertiaryContainer),
    onTertiaryContainer = animateColor(onTertiaryContainer),
    outline = animateColor(outline),
    surface = animateColor(primaryContainer),
    onSurface = animateColor(onPrimaryContainer),
    surfaceVariant = animateColor(surfaceVariant),
    onSurfaceVariant = animateColor(onSurfaceVariant),
    background = animateColor(primaryContainer),
    onBackground = animateColor(onPrimaryContainer)
)

@Composable
fun MidoriBrowserTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    privacy: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        privacy -> privateColorScheme
        darkTheme -> darkColorScheme
        else -> lightColorScheme
    }.animatedColors()

    val icons = if (privacy || darkTheme) darkAndPrivateIcons else lightIcons

    // TODO: This prevents previews from rendering with the qwant theme. Maybe this could be extracted in its own component.
    val view = LocalView.current
    val window = (view.context as Activity).window
    if (!view.isInEditMode) {
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
        LocalMidoriTheme provides MidoriTheme(darkTheme, privacy, icons)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(16.dp)),
            typography = Typography,
            content = content
        )
    }
}