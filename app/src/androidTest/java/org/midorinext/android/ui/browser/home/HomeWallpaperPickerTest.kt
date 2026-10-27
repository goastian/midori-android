package org.midorinext.android.ui.browser.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.HomeWallpaperCategory
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class HomeWallpaperPickerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scrollingToTheEndAndBackKeepsTheSheetAndViewportFixed() {
        var visible by mutableStateOf(true)
        var dismissed = false
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                if (visible) HomeWallpaperPicker(
                    current = HomeWallpaper.NONE,
                    onDismiss = { dismissed = true; visible = false },
                    onApply = {},
                )
            }
        }
        compose.waitForIdle()
        val originalSheet = bounds("home_wallpaper_picker")
        val originalGallery = bounds("home_wallpaper_gallery")
        val originalFilters = bounds("home_wallpaper_filter:all")

        compose.mainClock.autoAdvance = false
        repeat(6) {
            compose.onNodeWithTag("home_wallpaper_gallery").performTouchInput { swipeUp(durationMillis = 120) }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                assertGeometry(originalSheet, originalGallery, originalFilters)
            }
        }
        compose.mainClock.autoAdvance = true
        repeat(2) {
            compose.onNodeWithTag("home_wallpaper_gallery").performScrollToIndex(HomeWallpaper.entries.lastIndex)
            compose.waitForIdle()
            assertGeometry(originalSheet, originalGallery, originalFilters)
            repeat(3) {
                compose.onNodeWithTag("home_wallpaper_gallery").performTouchInput { swipeUp(durationMillis = 300) }
                compose.waitForIdle()
                assertGeometry(originalSheet, originalGallery, originalFilters)
            }
            compose.onNodeWithTag("home_wallpaper_gallery").performTouchInput { swipeDown(durationMillis = 300) }
            compose.waitForIdle()
            assertGeometry(originalSheet, originalGallery, originalFilters)
            compose.onNodeWithTag("home_wallpaper_gallery").performScrollToIndex(0)
            compose.waitForIdle()
            compose.waitUntil(5_000) {
                compose.onNodeWithTag("home_wallpaper_option:none").isDisplayed()
            }
            compose.onNodeWithTag("home_wallpaper_option:none").assertIsDisplayed()
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(compose.activity.cacheDir, "wallpaper-scroll-return.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()
            assertGeometry(originalSheet, originalGallery, originalFilters)
        }
        compose.runOnIdle { assertFalse("Scrolling the catalog must not dismiss the picker", dismissed) }
    }

    @Test
    fun galleryEdgeGesturesStayInsideTheGalleryAndTheHandleStillDismisses() {
        var visible by mutableStateOf(true)
        var dismissed = false
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                if (visible) HomeWallpaperPicker(
                    current = HomeWallpaper.NONE,
                    onDismiss = { dismissed = true; visible = false },
                    onApply = {},
                )
            }
        }
        compose.onNodeWithTag("home_wallpaper_gallery").performTouchInput { swipeDown(durationMillis = 300) }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithTag("home_wallpaper_picker").assertIsDisplayed()

        compose.onNodeWithTag("home_wallpaper_drag_handle", useUnmergedTree = true).performTouchInput {
            swipe(start = center, end = center + Offset(0f, 800f), durationMillis = 300)
        }
        compose.waitUntil(5_000) { dismissed }
        compose.onNodeWithTag("home_wallpaper_picker").assertDoesNotExist()
    }

    @Test
    fun travelPreviewKeepsItsFilterAndScrollPositionAndOnlyAppliesOnConfirmation() {
        var visible by mutableStateOf(true)
        var applied: HomeWallpaper? = null
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                if (visible) HomeWallpaperPicker(
                    current = HomeWallpaper.DEFAULT,
                    onDismiss = { visible = false },
                    onApply = { applied = it },
                )
            }
        }
        compose.onNodeWithTag("home_wallpaper_filter:travel").performScrollTo().performClick().assertIsSelected()
        val travel = HomeWallpaper.entries.filter { it.category == HomeWallpaperCategory.TRAVEL }
        compose.onNodeWithTag("home_wallpaper_gallery").performScrollToIndex(travel.lastIndex)
        compose.onNodeWithTag("home_wallpaper_option:${travel.last().id}").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_wallpaper_preview:${travel.last().id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home_wallpaper_credit").assertTextContains(travel.last().photographer!!, substring = true)
        compose.onNodeWithTag("home_wallpaper_back").performClick()
        compose.onNodeWithTag("home_wallpaper_filter:travel").assertIsSelected()
        compose.onNodeWithTag("home_wallpaper_option:${travel.last().id}").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, applied) }

        compose.onNodeWithTag("home_wallpaper_option:${travel.last().id}").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_wallpaper_preview:${travel.last().id}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home_wallpaper_apply").performClick()
        compose.runOnIdle { assertEquals(travel.last(), applied) }
        compose.onNodeWithTag("home_wallpaper_picker").assertDoesNotExist()
    }

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertGeometry(sheet: Rect, gallery: Rect, filters: Rect) {
        listOf("home_wallpaper_picker" to sheet, "home_wallpaper_gallery" to gallery,
            "home_wallpaper_filter:all" to filters).forEach { (tag, original) ->
            val current = bounds(tag)
            assertEquals("$tag top changed after scrolling", original.top, current.top, 2f)
            assertEquals("$tag height changed after scrolling", original.height, current.height, 2f)
        }
    }
}
