package org.midorinext.android.vip

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
fun VIPAction(
    vipState: VIPState,
    openLink: (String) -> Unit
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val iconId = when {
        vipState.enabled && isDarkTheme -> R.drawable.icons_vip_enabled_night
        vipState.enabled && !isDarkTheme -> R.drawable.icons_vip_enabled
        !vipState.enabled && isDarkTheme -> R.drawable.icons_vip_disabled_night
        !vipState.enabled && !isDarkTheme -> R.drawable.icons_vip_disabled
        else -> throw (Exception("Invalid icon for VIP action"))
    }

    Box {
        var showVIPPopup by remember { mutableStateOf(false) }
        ToolbarAction(onClick = {
            vipState.requestPopupSnapshot()
            showVIPPopup = true
        }) {
            Image(
                painter = painterResource(id = iconId),
                contentDescription = "Midori VIP",
                modifier = Modifier.fillMaxSize()
            )
        }

        Dropdown(
            expanded = showVIPPopup,
            onDismissRequest = { showVIPPopup = false }
        ) {
            VIPPopup(vipState, openLink)
        }
    }
}

