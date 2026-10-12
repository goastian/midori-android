package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

internal data class CachedTabThumbnail(val bitmap: Bitmap, val requestedSize: Int)

/** A tray owns this cache; changing browsing mode or leaving the tray releases it. */
internal class TabThumbnailMemoryCache {
    private data class Key(val tabId: String, val private: Boolean)

    private val images = object : LruCache<Key, CachedTabThumbnail>(16 * 1024 * 1024) {
        override fun sizeOf(key: Key, value: CachedTabThumbnail): Int = value.bitmap.allocationByteCount
    }

    fun get(tabId: String, private: Boolean): CachedTabThumbnail? = images.get(Key(tabId, private))

    fun put(tabId: String, private: Boolean, bitmap: Bitmap, requestedSize: Int) {
        val key = Key(tabId, private)
        val previous = images.get(key)
        if (requestedSize >= (previous?.requestedSize ?: 0)) {
            // A larger request can fall back to a smaller decode. Keep the sharper image
            // while remembering that the requested size has already been attempted.
            val bestBitmap = previous?.bitmap?.takeIf {
                maxOf(it.width, it.height) > maxOf(bitmap.width, bitmap.height)
            } ?: bitmap
            images.put(key, CachedTabThumbnail(bestBitmap, requestedSize))
        }
    }

    // Do not recycle: a retiring frame may still reference one of the displayed bitmaps.
    fun clear() = images.evictAll()
}

internal val LocalTabThumbnailCache = staticCompositionLocalOf<TabThumbnailMemoryCache?> { null }

@Composable
internal fun TabThumbnailCacheProvider(private: Boolean, content: @Composable () -> Unit) {
    val cache = remember(private) { TabThumbnailMemoryCache() }
    DisposableEffect(cache) {
        onDispose { cache.clear() }
    }
    CompositionLocalProvider(LocalTabThumbnailCache provides cache, content = content)
}
