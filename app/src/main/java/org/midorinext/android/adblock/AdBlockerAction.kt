package org.midorinext.android.adblock

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.theme.LocalMidoriTheme

@Composable
fun AdBlockerAction(
    protectionEnabled: Boolean,
    onClick: () -> Unit,
) {
    val isDarkTheme = LocalMidoriTheme.current.dark
    val iconId = when {
        protectionEnabled && isDarkTheme -> R.drawable.icons_vip_enabled_night
        protectionEnabled -> R.drawable.icons_vip_enabled
        isDarkTheme -> R.drawable.icons_vip_disabled_night
        else -> R.drawable.icons_vip_disabled
    }

    ToolbarAction(onClick = onClick) {
        Image(
            painter = painterResource(id = iconId),
            contentDescription = stringResource(R.string.native_blocker_title),
            modifier = Modifier.size(24.dp)
        )
    }
}
