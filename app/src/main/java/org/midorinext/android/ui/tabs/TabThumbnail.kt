package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CancellationException
import org.midorinext.android.contentBlocker.ContentBlockerOverlay
import org.midorinext.android.contentBlocker.ContentBlockerState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageLoadRequest

@Composable
fun TabThumbnail(
    tabId: String,
    private: Boolean,
    thumbnailStorage: ThumbnailStorage,
    contentBlockerState: ContentBlockerState,
    modifier: Modifier = Modifier
) {
    val contentBlockerStatus = contentBlockerState.getStatusForTab(tabId)
    var pixelSize by remember(tabId) { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size -> pixelSize = maxOf(size.width, size.height) },
    ) {
        if (contentBlockerStatus != ContentBlockerState.Status.ALLOWED) {
            ContentBlockerOverlay(
                status = contentBlockerStatus,
                blockReason = contentBlockerState.getBlockReasonForTab(tabId),
            )
            return@Box
        }

        var loadedImage: Bitmap? by remember(tabId, private) { mutableStateOf(null) }

        LaunchedEffect(tabId, pixelSize, private) {
            if (pixelSize <= 0) return@LaunchedEffect

            loadedImage = null
            loadedImage = try {
                thumbnailStorage.loadThumbnail(ImageLoadRequest(tabId, pixelSize, private)).await()
            } catch (cancelled: CancellationException) {
                // Preserve structured cancellation when a recycled card starts loading another
                // thumbnail. Swallowing it would keep obsolete work alive during fast scrolling.
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }

        loadedImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
