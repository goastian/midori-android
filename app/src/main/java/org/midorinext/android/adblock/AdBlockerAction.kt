package org.midorinext.android.adblock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.midorinext.android.R
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.theme.LocalMidoriTheme

@Composable
fun AdBlockerAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val iconId = when {
        enabled && isDarkTheme -> R.drawable.icons_vip_enabled_night
        enabled -> R.drawable.icons_vip_enabled
        isDarkTheme -> R.drawable.icons_vip_disabled_night
        else -> R.drawable.icons_vip_disabled
    }

    ToolbarAction(onClick = onClick, enabled = enabled) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = "Midori Privacy",
            modifier = Modifier.fillMaxSize()
        )
    }
}
