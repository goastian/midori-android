package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageSaveRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.R
import org.midorinext.android.newtab.NATIVE_HOME_URL
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import java.util.UUID

class TabPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val tabId = "preview-test-${UUID.randomUUID()}"
    private val storage by lazy { ThumbnailStorage(compose.activity) }

    @After
    fun clearFixture() = runBlocking {
        storage.deleteThumbnail(tabId, false).join()
    }

    @Test
    fun tallPreviewPreservesBothEdgesOfThePage() {
        savePreview()
        showPreview("https://preview.example")
        val bitmap = compose.onNodeWithTag("tab_thumbnail:$tabId", useUnmergedTree = true)
            .captureToImage().asAndroidBitmap()
        try {
            val left = bitmap.getPixel(4, 8)
            val right = bitmap.getPixel(bitmap.width - 5, 8)
            assertTrue("The page's left edge must remain visible", Color.red(left) > 200 && Color.blue(left) < 60)
            assertTrue("The page's right edge must remain visible", Color.blue(right) > 200 && Color.red(right) < 60)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun nativeHomeUsesItsSavedPreview() {
        savePreview()
        showPreview(NATIVE_HOME_URL)
        compose.onNodeWithTag("tab_thumbnail_loaded:$tabId", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun privateHomeDoesNotLoadANormalHomePreview() {
        savePreview()
        compose.setContent {
            MidoriBrowserTheme {
                TabPreview(TabSessionState(tabId, ContentState(NATIVE_HOME_URL, private = true)), storage)
            }
        }
        compose.onNodeWithTag("tab_thumbnail:$tabId", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun leavingTheLayoutDuringExitStillClosesTheTabOnce() {
        var visible by mutableStateOf(true)
        var closed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MidoriBrowserTheme {
                if (visible) {
                    TabRow(
                        TabSessionState(tabId, ContentState(NATIVE_HOME_URL, private = true)),
                        selected = false, thumbnailStorage = storage,
                        onSelected = {}, onDeleted = { closed++ },
                    )
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.tab_tray_close_tab)).performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { visible = false }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test
    fun closingARowWaitsForItsExitAndInvokesTheCallbackOnce() {
        var closed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MidoriBrowserTheme {
                TabRow(
                    TabSessionState(tabId, ContentState(NATIVE_HOME_URL, private = true)),
                    selected = false, thumbnailStorage = storage,
                    onSelected = {}, onDeleted = { closed++ },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val close = compose.onNodeWithContentDescription(compose.activity.getString(R.string.tab_tray_close_tab))
        close.performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { assertEquals(0, closed) }
        close.assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(1, closed) }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(1, closed) }
    }

    private fun showPreview(url: String) {
        compose.setContent {
            MidoriBrowserTheme {
                CompositionLocalProvider(LocalReduceMotion provides true) {
                    Box(Modifier.width(100.dp).height(260.dp)) {
                        TabPreview(TabSessionState(tabId, ContentState(url)), storage)
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("tab_thumbnail_loaded:$tabId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun savePreview() = runBlocking {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.GREEN)
        val paint = Paint().apply { color = Color.RED }
        canvas.drawRect(0f, 0f, 60f, 900f, paint)
        paint.color = Color.BLUE
        canvas.drawRect(540f, 0f, 600f, 900f, paint)
        storage.saveThumbnail(ImageSaveRequest(tabId, false), bitmap).join()
        bitmap.recycle()
    }
}
