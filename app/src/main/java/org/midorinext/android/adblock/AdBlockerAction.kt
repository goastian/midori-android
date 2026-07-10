package org.midorinext.android.adblock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.midorinext.android.R
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.widgets.Dropdown

@Composable
fun AdBlockerAction(
    adBlockerState: AdBlockerState,
    openLink: (String) -> Unit
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val iconId = when {
        adBlockerState.enabled && isDarkTheme -> R.drawable.icons_vip_enabled_night
        adBlockerState.enabled && !isDarkTheme -> R.drawable.icons_vip_enabled
        !adBlockerState.enabled && isDarkTheme -> R.drawable.icons_vip_disabled_night
        else -> R.drawable.icons_vip_disabled
    }

    Box {
        var showPopup by remember { mutableStateOf(false) }
        ToolbarAction(onClick = {
            showPopup = true
        }) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = "Midori Privacy",
                modifier = Modifier.fillMaxSize()
            )
        }

        Dropdown(
            expanded = showPopup,
            onDismissRequest = { showPopup = false }
        ) {
            AdBlockerPopup(adBlockerState, openLink)
        }
    }
}
