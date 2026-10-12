package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabThumbnailMemoryCacheTest {
    @Test
    fun privateAndNormalImagesStaySeparateAndAreReleasedWithTheTray() {
        val cache = TabThumbnailMemoryCache()
        val normal = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val private = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            cache.put("same-id", false, normal, 256)
            assertNull(cache.get("same-id", true))
            cache.put("same-id", true, private, 256)
            assertSame(normal, cache.get("same-id", false)?.bitmap)
            assertSame(private, cache.get("same-id", true)?.bitmap)
            cache.clear()
            assertNull(cache.get("same-id", false))
            assertNull(cache.get("same-id", true))
        } finally {
            normal.recycle()
            private.recycle()
        }
    }
}
