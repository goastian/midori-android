package org.midorinext.android.ui.browser.home

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import mozilla.components.support.ktx.android.content.isOSOnLowMemory
import kotlin.math.roundToInt

@Composable
internal fun rememberNativeHomeThumbnail(
    tabId: String?,
    contentKey: Any,
    scrolling: Boolean,
    onThumbnail: (String, Bitmap) -> Unit,
): Modifier {
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val saveThumbnail by rememberUpdatedState(onThumbnail)
    var captureRequested by remember(tabId) { mutableStateOf(false) }
    LaunchedEffect(tabId, contentKey, scrolling) {
        if (tabId == null || scrolling || context.isOSOnLowMemory()) return@LaunchedEffect
        delay(350)
        try {
            captureRequested = true
            withFrameNanos { }
            withFrameNanos { }
            if (layer.size.width > 0 && layer.size.height > 0) {
                saveThumbnail(tabId, layer.toImageBitmap().asAndroidBitmap())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A preview is optional when the rendering surface is no longer available.
        } finally {
            captureRequested = false
        }
    }
    return Modifier.drawWithContent {
        if (captureRequested) {
            val previewScale = (1024f / maxOf(size.width, size.height)).coerceAtMost(1f)
            val previewSize = IntSize(
                (size.width * previewScale).roundToInt().coerceAtLeast(1),
                (size.height * previewScale).roundToInt().coerceAtLeast(1),
            )
            layer.record(size = previewSize) {
                scale(previewScale, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
            }
        }
        drawContent()
    }
}
