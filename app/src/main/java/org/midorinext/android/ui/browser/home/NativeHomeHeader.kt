package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.ui.theme.LocalMidoriTheme

@Composable
fun NativeHomeHeader(
    private: Boolean,
    onSwitchBrowsingMode: () -> Unit,
    modifier: Modifier = Modifier,
    showLogo: Boolean = true,
    onCustomize: (() -> Unit)? = null,
    wallpaper: Boolean = false,
) {
    val theme = LocalMidoriTheme.current
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showLogo) {
            val logo = painterResource(R.drawable.logomidori)
            Box(Modifier.height(28.dp)
                    .aspectRatio(logo.intrinsicSize.width / logo.intrinsicSize.height)
                    .semantics(mergeDescendants = true) {}
                    .testTag("native_home_brand")) {
                Image(logo, "Midori", Modifier.matchParentSize())
                if (wallpaper || theme.dark || theme.private) {
                    Image(logo, null, Modifier.matchParentSize().drawWithContent {
                        clipRect(left = size.height) { this@drawWithContent.drawContent() }
                    }, colorFilter = ColorFilter.tint(if (wallpaper) Color.White else MaterialTheme.colorScheme.onSurface))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (onCustomize != null) {
            IconButton(onClick = onCustomize, modifier = Modifier.testTag("native_home_customize")) {
                Icon(
                    painterResource(R.drawable.icons_edit),
                    stringResource(R.string.menu_customize_homepage),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
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
