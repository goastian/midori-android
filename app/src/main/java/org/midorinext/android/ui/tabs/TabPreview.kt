package org.midorinext.android.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.newtab.isNativeHomeUrl

@Composable
internal fun TabPreview(
    tab: TabSessionState,
    thumbnailStorage: ThumbnailStorage,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.FillWidth,
) {
    val privateHome = tab.content.private && (tab.content.url.isBlank() || isNativeHomeUrl(tab.content.url))
    val icon = tab.content.icon
    val placeholder: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                privateHome -> Icon(
                    painterResource(R.drawable.icons_privacy_mask), null,
                    Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary,
                )
                isNativeHomeUrl(tab.content.url) -> Image(
                    painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(48.dp),
                )
                icon != null -> Image(
                    icon.asImageBitmap(), null, Modifier.size(24.dp),
                )
                else -> Icon(
                    painterResource(R.drawable.icons_internet), null,
                    Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                )
            }
        }
    }
    if (privateHome) {
        Box(modifier) { placeholder() }
    } else {
        TabThumbnail(tab.id, tab.content.private, thumbnailStorage, modifier, contentScale, placeholder)
    }
}
