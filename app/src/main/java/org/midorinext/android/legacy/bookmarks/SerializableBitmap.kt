package org.midorinext.android.legacy.bookmarks

import android.graphics.Bitmap
import java.io.Serializable

class SerializableBitmap(bitmap: Bitmap) : Serializable {
    private val pixels: IntArray
    private val width: Int = bitmap.width
    private val height: Int = bitmap.height
    val bitmap: Bitmap
        get() = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

    init {
        pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    }
}