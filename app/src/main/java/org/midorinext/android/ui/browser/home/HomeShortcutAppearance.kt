package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.HomeShortcutShape

fun homeShortcutIconShape(shape: HomeShortcutShape): Shape = when (shape) {
    HomeShortcutShape.HOME_SHORTCUT_CIRCLE -> CircleShape
    HomeShortcutShape.HOME_SHORTCUT_SQUARE -> RectangleShape
    else -> RoundedCornerShape(12.dp)
}
