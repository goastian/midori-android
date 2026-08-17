package org.midorinext.android.vpn

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.midorinext.android.R
import org.midorinext.android.ui.browser.ToolbarAction

@Composable
fun MidoriVpnAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ToolbarAction(onClick = onClick, enabled = enabled) {
        Image(
            painter = painterResource(id = R.drawable.ic_midori_vpn_action),
            contentDescription = stringResource(R.string.midori_vpn_action_content_description),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
