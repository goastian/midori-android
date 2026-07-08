package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import org.midorinext.android.contentBlocker.ContentBlockerOverlay
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ui.theme.LocalMidoriTheme
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageLoadRequest

@Composable
fun TabThumbnail(
    tabId: String,
    size: Dp,
    thumbnailStorage: ThumbnailStorage,
    contentBlockerState: ContentBlockerState,
    modifier: Modifier = Modifier
) {
    val contentBlockerStatus = contentBlockerState.getStatusForTab(tabId)
    if (contentBlockerStatus != ContentBlockerState.Status.ALLOWED) {
        ContentBlockerOverlay(status = contentBlockerStatus, blockReason = contentBlockerState.getBlockReasonForTab(tabId))
    } else {
        val pixelSize = with(LocalDensity.current) { size.roundToPx() }
        var loadedImage: Bitmap? by remember { mutableStateOf(null) }

        val private = LocalMidoriTheme.current.private

        LaunchedEffect(tabId) {
            loadedImage = thumbnailStorage.loadThumbnail(ImageLoadRequest(id = tabId, pixelSize , private)).await()
        }

        loadedImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Tab Thumbnail",
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = modifier.fillMaxWidth()
            )
        }
    }
}