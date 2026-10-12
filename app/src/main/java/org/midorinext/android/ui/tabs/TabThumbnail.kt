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
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageLoadRequest

@Composable
fun TabThumbnail(
    tabId: String,
    private: Boolean,
    thumbnailStorage: ThumbnailStorage,
    modifier: Modifier = Modifier
) {
    val cache = LocalTabThumbnailCache.current
    var pixelSize by remember(tabId) { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tab_thumbnail:$tabId")
            .onSizeChanged { size -> pixelSize = maxOf(size.width, size.height) },
    ) {

        var loadedImage: Bitmap? by remember(tabId, private, cache) {
            mutableStateOf(cache?.get(tabId, private)?.bitmap)
        }

        LaunchedEffect(tabId, pixelSize, private, cache) {
            if (pixelSize <= 0) return@LaunchedEffect
            val cached = cache?.get(tabId, private)
            if (cached != null && cached.requestedSize >= pixelSize) {
                loadedImage = cached.bitmap
                return@LaunchedEffect
            }

            // Preserve the current image while a larger version is decoded after resizing.
            val decoded = try {
                var requestSize = pixelSize
                var bitmap = thumbnailStorage.loadThumbnail(ImageLoadRequest(tabId, requestSize, private)).await()
                // The decoder rejects requests beyond the stored image's upscale limit.
                // A tall carousel must still be able to display a smaller saved thumbnail.
                while (bitmap == null && requestSize > 64) {
                    requestSize = (requestSize / 2).coerceAtLeast(64)
                    bitmap = thumbnailStorage.loadThumbnail(ImageLoadRequest(tabId, requestSize, private)).await()
                }
                bitmap
            } catch (cancelled: CancellationException) {
                // Preserve structured cancellation when a recycled card starts loading another
                // thumbnail. Swallowing it would keep obsolete work alive during fast scrolling.
                throw cancelled
            } catch (_: Throwable) {
                null
            }
            if (decoded != null) {
                cache?.put(tabId, private, decoded, pixelSize)
                loadedImage = cache?.get(tabId, private)?.bitmap ?: decoded
            }
        }

        loadedImage?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().testTag("tab_thumbnail_loaded:$tabId"),
            )
        }
    }
}
