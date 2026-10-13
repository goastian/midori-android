package org.midorinext.android.ui.browser.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class NativeHomeThumbnailTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun capturesTheDrawnSurfaceAtABoundedSizeAndRefreshesForTheNextTab() {
        var tabId by mutableStateOf("first-home")
        var color by mutableStateOf(Color.Red)
        val captured = AtomicReference<Pair<String, Bitmap>?>()
        compose.setContent {
            val thumbnail = rememberNativeHomeThumbnail(tabId, color, scrolling = false) { id, bitmap ->
                captured.getAndSet(id to bitmap)?.second?.recycle()
            }
            Box(Modifier.fillMaxSize().then(thumbnail).background(color))
        }
        compose.waitUntil(10_000) { captured.get()?.first == "first-home" }
        compose.runOnIdle {
            val bitmap = captured.get()!!.second
            assertCaptureColor(android.graphics.Color.RED, bitmap)
            assertTrue(maxOf(bitmap.width, bitmap.height) <= 1024)
            tabId = "second-home"
            color = Color.Blue
        }
        compose.waitUntil(10_000) { captured.get()?.first == "second-home" }
        compose.runOnIdle {
            val bitmap = captured.get()!!.second
            assertCaptureColor(android.graphics.Color.BLUE, bitmap)
            bitmap.recycle()
        }
    }

    private fun assertCaptureColor(expected: Int, bitmap: Bitmap) {
        val readable = checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false))
        try {
            assertEquals(expected, readable.getPixel(readable.width / 2, readable.height / 2))
        } finally {
            readable.recycle()
        }
    }
}
