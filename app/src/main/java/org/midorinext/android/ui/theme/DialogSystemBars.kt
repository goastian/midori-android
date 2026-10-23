package org.midorinext.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
internal fun MidoriDialogSystemBars() {
    val view = LocalView.current
    val theme = LocalMidoriTheme.current
    val useDarkIcons = !(theme.dark || theme.private)
    // Material3 1.4 sets these flags only when its bottom-sheet window is created.
    SideEffect {
        val window = generateSequence(view.parent) { it.parent }
            .filterIsInstance<DialogWindowProvider>()
            .firstOrNull()?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = useDarkIcons
                isAppearanceLightNavigationBars = useDarkIcons
            }
        }
    }
}
