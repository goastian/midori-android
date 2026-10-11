package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.R

@Composable
fun NativeHomeHeader(
    private: Boolean,
    onSwitchBrowsingMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val logo = painterResource(R.drawable.logomidori)
        Image(
            logo,
            "Midori",
            Modifier.height(28.dp)
                .aspectRatio(logo.intrinsicSize.width / logo.intrinsicSize.height)
                .testTag("native_home_brand"),
        )
        Spacer(Modifier.weight(1f))
        IconToggleButton(
            checked = private,
            onCheckedChange = { onSwitchBrowsingMode() },
            modifier = Modifier.testTag(if (private) "native_home_normal" else "native_home_private"),
        ) {
            Icon(
                painterResource(if (private) R.drawable.icons_tab_smiley else R.drawable.icons_privacy_mask),
                stringResource(if (private) R.string.home_normal else R.string.home_private),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
