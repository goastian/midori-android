package org.midorinext.android.ui.tabs

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.base.images.ImageSaveRequest
import mozilla.components.concept.base.images.ImageLoadRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.BuildConfig
import org.midorinext.android.MainActivity
import org.midorinext.android.MidoriApplication
import org.midorinext.android.R
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.theme.PrivateSurfaceDark
import org.midorinext.android.ui.theme.darkColorScheme
import org.midorinext.android.ui.theme.lightColorScheme
import org.midorinext.android.preferences.app.*

/** Full tray checks using local thumbnails and unloaded .example tabs, with no live websites. */
@RunWith(AndroidJUnit4::class)
class TabsLayoutsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: AppPreferences
    private lateinit var tabsViewModel: TabsScreenViewModel
    private lateinit var applicationViewModel: MidoriApplicationViewModel
    private val fixtureIds = mutableListOf<String>()
    private val privateFixtureIds = mutableListOf<String>()
    private var originalSelection: String? = null
    private var onboardingEnabled = false
    private val groupId = "tabs-layout-device-fixture"
    private val fixtureTitles = listOf("Weekend routes", "Places to stay", "Reading for later", "Design journal", "Trail notes")

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        preferences = app.appPreferencesRepository.get().flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        onboardingEnabled = shared.getBoolean(app.getString(R.string.pref_key_show_onboarding), true)
        shared.edit().putBoolean(app.getString(R.string.pref_key_show_onboarding), false).commit()
        app.appPreferencesRepository.get().apply {
            updateAppearance(Appearance.LIGHT)
            updateToolbarPosition(ToolbarPosition.TOP)
            updateTabsView(TabsViewOption.GRID)
            updateOpenBlankNewTab(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        originalSelection = app.store.get().state.selectedTabId
        scenario.onActivity {
            tabsViewModel = ViewModelProvider(it)[TabsScreenViewModel::class.java]
            applicationViewModel = ViewModelProvider(it)[MidoriApplicationViewModel::class.java]
        }
        val titles = fixtureTitles
        for ((index, title) in titles.withIndex()) {
            val url = "https://page$index.example/article?fixture=hidden"
            var id = ""
            instrumentation.runOnMainSync {
                id = app.tabsUseCases.get().addTab(url, title = title, selectTab = index == titles.lastIndex, startLoading = false)
                fixtureIds += id
                val icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 120, 86)) }
                app.store.get().dispatch(ContentAction.UpdateIconAction(id, url, icon))
            }
            val preview = fixturePreview(title, index)
            tabsViewModel.thumbnailStorage.saveThumbnail(ImageSaveRequest(id, false), preview).join()
            preview.recycle()
        }
        compose.waitUntil(10_000) { fixtureIds.all { id -> app.store.get().state.tabs.any { it.id == id } } }
        app.appPreferencesRepository.get().apply {
            updateTabGroups { groups -> groups + SavedTabGroup.newBuilder().setId(groupId).setName("Weekend plans").addAllTabIds(fixtureIds.take(2)).build() }
            updateTabGroupColor(groupId, TabGroupColor.GREEN.value)
        }
        compose.waitForIdle()
        val count = app.store.get().state.tabs.count { !it.content.private }
        val description = app.getString(R.string.tab_tray_title, count)
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_layout_toggle").fetchSemanticsNodes().isNotEmpty() }
    }

    @After
    fun restore(): Unit = runBlocking {
        if (::preferences.isInitialized) {
            app.appPreferencesRepository.get().apply {
                updateTabGroups { it.filterNot { group -> group.id == groupId } }
                removeTabGroupColor(groupId)
                updateAppearance(preferences.appearance)
                updateToolbarPosition(preferences.toolbarPosition)
                updateTabsView(preferences.tabsView)
                updateOpenBlankNewTab(preferences.openBlankNewTab)
            }
        }
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            (fixtureIds + privateFixtureIds).forEach { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
        }
        if (::tabsViewModel.isInitialized) {
            fixtureIds.forEach { tabsViewModel.thumbnailStorage.deleteThumbnail(it, false).join() }
            privateFixtureIds.forEach { tabsViewModel.thumbnailStorage.deleteThumbnail(it, true).join() }
        }
        fixtureTitles.indices.forEach { app.historyRepository.get().deleteVisitsFor("https://page$it.example/article?fixture=hidden") }
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboardingEnabled).commit()
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun nativeHomeIsCapturedAndDisplayedInGridAndCarousel(): Unit = runBlocking {
        compose.onNode(
            hasText(app.getString(R.string.browser_new_tab)) and
                !hasAnyAncestor(hasTestTag("tabs_grid"))
        ).performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("native_home").fetchSemanticsNodes().isNotEmpty()
        }
        val homeId = checkNotNull(app.store.get().state.selectedTabId)
        fixtureIds += homeId
        compose.waitUntil(15_000) {
            runBlocking {
                tabsViewModel.thumbnailStorage.loadThumbnail(ImageLoadRequest(homeId, 64, false)).await()
                    ?.let { it.recycle(); true } ?: false
            }
        }
        for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
            scenario.onActivity { it.requestedOrientation = orientation }
            compose.waitUntil(15_000) {
                runBlocking {
                    tabsViewModel.thumbnailStorage.loadThumbnail(ImageLoadRequest(homeId, 64, false)).await()
                        ?.let { bitmap ->
                            val landscape = bitmap.width > bitmap.height
                            bitmap.recycle()
                            landscape == (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                        } ?: false
                }
            }
        }
        openTray(private = false)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("tab_thumbnail_loaded:$homeId", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("tab_card:$homeId").assertIsDisplayed()
        capture("tabs-home-preview-grid.png")
        cycleLayoutTo(TabsViewOption.CAROUSEL)
        compose.onNodeWithTag("tab_card:$homeId").assertIsDisplayed()
        capture("tabs-home-preview-carousel.png")
    }

    @Test
    fun carouselPreviewsNavigateWithoutChangingTheActiveSession(): Unit = runBlocking {
        val selectedId = app.store.get().state.selectedTabId
        val ids = app.store.get().state.tabs.map { it.id }
        cycleLayoutTo(TabsViewOption.CAROUSEL)
        val targetId = fixtureIds[2]
        val strip = compose.onNodeWithTag("tabs_carousel_previews")
        strip.performScrollToNode(hasTestTag("tabs_carousel_preview:$targetId"))
        compose.onNodeWithTag("tabs_carousel_preview:$targetId").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tabs_carousel_preview:$targetId").assertIsSelected()
        compose.onNodeWithTag("tab_card:$targetId").assertIsDisplayed()
        assertEquals(selectedId, app.store.get().state.selectedTabId)
        assertEquals(ids, app.store.get().state.tabs.map { it.id })
        capture("tabs-carousel-previews.png")
    }

    @Test
    fun changingLayoutsPreservesTabsAndRendersBothAppearances(): Unit = runBlocking {
        val ids = app.store.get().state.tabs.map { it.id }
        val selectedId = app.store.get().state.selectedTabId
        var firstLayout = true
        for (appearance in listOf(Appearance.LIGHT, Appearance.DARK)) {
            app.appPreferencesRepository.get().updateAppearance(appearance)
            for (layout in listOf(TabsViewOption.GRID, TabsViewOption.CAROUSEL, TabsViewOption.LIST)) {
                if (firstLayout) {
                    firstLayout = false
                    assertLayout(layout)
                } else {
                    cycleLayoutTo(layout)
                }
                compose.onNodeWithTag("tabs_${layout.name.lowercase()}").performScrollToIndex(0)
                compose.waitForIdle()
                assertEquals(layout, app.appPreferencesRepository.get().flow.first().tabsView)
                assertEquals(ids, app.store.get().state.tabs.map { it.id })
                assertEquals(selectedId, app.store.get().state.selectedTabId)
                capture("tabs-${layout.name.lowercase()}-${appearance.name.lowercase()}.png")
            }
            assertPrivateSwitchHasImmediateSurface(appearance)
        }
        cycleLayoutTo(TabsViewOption.GRID)
        cycleLayoutTo(TabsViewOption.CAROUSEL)
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(1)
        compose.waitForIdle()
        capture("tabs-carousel-page-dark.png")
        compose.onNodeWithTag("tabs_carousel").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(ids, app.store.get().state.tabs.map { it.id })
        assertEquals(selectedId, app.store.get().state.selectedTabId)

        // Selection inside an expanded group must remain in the tray, and horizontal
        // swiping must still work while grouped tabs are selected.
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(0)
        compose.onNodeWithTag("tab_group:$groupId").performClick()
        compose.onNodeWithTag("tab_group_tabs:$groupId").performScrollToNode(hasTestTag("tab_card:${fixtureIds[0]}"))
        compose.onNodeWithTag("tab_card:${fixtureIds[0]}").performTouchInput { longClick() }
        compose.onNodeWithTag("tab_card:${fixtureIds[0]}").assertIsSelected()
        compose.onNodeWithTag("tab_group_tabs:$groupId").performScrollToNode(hasTestTag("tab_card:${fixtureIds[1]}"))
        compose.onNodeWithTag("tab_card:${fixtureIds[1]}").performClick().assertIsSelected()
        compose.onNodeWithTag("tab_group_collapse:$groupId").performClick()
        compose.onNodeWithTag("tab_group_expanded:$groupId").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.browser_group_tabs_selected, 2)).assertIsDisplayed()
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(1)
        val carousel = compose.onNodeWithTag("tabs_carousel")
        val beforeSwipe = carousel.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        carousel.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val afterSwipe = carousel.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Carousel must scroll during selection with a group present", afterSwipe > beforeSwipe)
        assertEquals(ids, app.store.get().state.tabs.map { it.id })
        assertEquals(selectedId, app.store.get().state.selectedTabId)
        capture("tabs-carousel-selection-dark.png")

        compose.onNodeWithText(app.getString(R.string.tab_tray_done)).performClick()
        scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_carousel").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(TabsViewOption.CAROUSEL, app.appPreferencesRepository.get().flow.first().tabsView)
        assertEquals(ids, app.store.get().state.tabs.map { it.id })
        assertEquals(selectedId, app.store.get().state.selectedTabId)

        compose.onNodeWithTag("tabs_page_PRIVATE").performClick().assertIsSelected()
        assertNoNormalFixtures()
        compose.onNodeWithText(app.getString(R.string.browser_new_tab_private)).assertIsDisplayed()
        if (app.store.get().state.tabs.none { it.content.private }) {
            compose.onNodeWithText(app.getString(R.string.browser_tabs_empty_title_private)).assertIsDisplayed()
        }
        assertEquals(ids, app.store.get().state.tabs.map { it.id })
        assertEquals(selectedId, app.store.get().state.selectedTabId)
        capture("tabs-private-dark.png")

        compose.onNodeWithText(app.getString(R.string.browser_new_tab_private)).performClick()
        compose.waitUntil(20_000) {
            app.store.get().state.tabs.any { it.id == app.store.get().state.selectedTabId && it.content.private }
        }
        val privateId = checkNotNull(app.store.get().state.selectedTabId)
        privateFixtureIds += privateId
        val idsWithPrivate = app.store.get().state.tabs.map { it.id }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_layout_toggle").fetchSemanticsNodes().isEmpty() }
        openTray(private = true)
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        assertNoNormalFixtures()
        assertLayout(TabsViewOption.CAROUSEL)

        repeat(2) {
            compose.onNodeWithTag("tabs_page_NORMAL").performClick().assertIsSelected()
            compose.onNodeWithTag("tab_card:$privateId").assertDoesNotExist()
            assertEquals(privateId, app.store.get().state.selectedTabId)
            assertEquals(idsWithPrivate, app.store.get().state.tabs.map { it.id })
            compose.onNodeWithTag("tabs_page_PRIVATE").performClick().assertIsSelected()
            assertNoNormalFixtures()
            assertEquals(privateId, app.store.get().state.selectedTabId)
        }

        // Browsing the other tray does not switch the page displayed after leaving it.
        compose.onNodeWithTag("tabs_page_NORMAL").performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.nav_back)).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_layout_toggle").fetchSemanticsNodes().isEmpty() }
        assertEquals(privateId, app.store.get().state.selectedTabId)
        compose.waitUntil(10_000) { applicationViewModel.isPrivate.value }
        compose.onNodeWithTag("native_private_home").assertIsDisplayed()
        compose.onNodeWithTag("native_home").assertDoesNotExist()
        openTray(private = true)
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        assertNoNormalFixtures()

        // Opening an actual normal tab changes the browser session and its next tray.
        compose.onNodeWithTag("tabs_page_NORMAL").performClick()
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(1)
        compose.onNodeWithTag("tab_card:${fixtureIds.last()}").performClick()
        compose.waitUntil(20_000) { app.store.get().state.selectedTabId == fixtureIds.last() }
        compose.waitUntil(10_000) { !applicationViewModel.isPrivate.value }
        openTray(private = false)
        compose.onNodeWithTag("tabs_page_NORMAL").assertIsSelected()
        compose.onNodeWithTag("tab_card:$privateId").assertDoesNotExist()
        assertEquals(idsWithPrivate, app.store.get().state.tabs.map { it.id })
        assertLayout(TabsViewOption.CAROUSEL)
    }

    private suspend fun cycleLayoutTo(expected: TabsViewOption) {
        compose.onNodeWithTag("tabs_layout_toggle").performClick()
        assertLayout(expected)
    }

    private fun assertPrivateSwitchHasImmediateSurface(appearance: Appearance) {
        val normalSurface = if (appearance == Appearance.DARK) {
            darkColorScheme.primaryContainer
        } else {
            lightColorScheme.primaryContainer
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithTag("tabs_page_PRIVATE").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
            assertTraySurface(PrivateSurfaceDark.toArgb(), "First private frame from $appearance")
            assertNoNormalFixtures()

            compose.onNodeWithTag("tabs_page_NORMAL").performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("tabs_page_NORMAL").assertIsSelected()
            assertTraySurface(normalSurface.toArgb(), "First normal frame from private in $appearance")
        } finally {
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
    }

    private fun assertTraySurface(expectedColor: Int, message: String) {
        val bitmap = compose.onNodeWithTag("tabs_screen").captureToImage().asAndroidBitmap()
        try {
            assertEquals(message, expectedColor, bitmap.getPixel(2, 2))
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun assertLayout(expected: TabsViewOption) {
        val label = app.getString(when (expected) {
            TabsViewOption.GRID -> R.string.available_tabs_view_grid
            TabsViewOption.CAROUSEL -> R.string.available_tabs_view_carousel
            else -> R.string.available_tabs_view_list
        })
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                hasTestTag("tabs_layout_toggle") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        withTimeout(10_000) { app.appPreferencesRepository.get().flow.first { it.tabsView == expected } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("tabs_${expected.name.lowercase()}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun assertNoNormalFixtures() {
        fixtureIds.forEach { id ->
            compose.onNodeWithTag("tab_card:$id").assertDoesNotExist()
            compose.onNodeWithTag("tab_row:$id").assertDoesNotExist()
            compose.onAllNodesWithTag("tab_thumbnail:$id", useUnmergedTree = true).assertCountEquals(0)
        }
        compose.onNodeWithTag("tab_group:$groupId").assertDoesNotExist()
    }

    private fun openTray(private: Boolean) {
        val count = app.store.get().state.tabs.count { it.content.private == private }
        val description = if (count == 1) app.getString(R.string.tab_tray_title_single)
            else app.getString(R.string.tab_tray_title, count)
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_layout_toggle").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(name: String) {
        // Selecting an unloaded fixture can still attach Gecko and finish on its local
        // DNS error page. Restore the synthetic labels after opening the tray so the
        // screenshots consistently describe the local thumbnail fixtures.
        instrumentation.runOnMainSync {
            fixtureIds.zip(fixtureTitles).forEach { (id, title) ->
                app.store.get().dispatch(ContentAction.UpdateTitleAction(id, title))
            }
        }
        compose.waitUntil(10_000) {
            fixtureIds.zip(fixtureTitles).all { (id, title) -> app.store.get().state.tabs.firstOrNull { it.id == id }?.content?.title == title }
        }
        compose.waitForIdle()
        var missingThumbnails = emptyList<String>()
        try {
            compose.waitUntil(10_000) {
                val thumbnails = compose.onAllNodes(
                    SemanticsMatcher("tab thumbnail") {
                        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("tab_thumbnail:") == true
                    },
                    useUnmergedTree = true,
                ).fetchSemanticsNodes().filter { it.boundsInRoot.width > 0 && it.boundsInRoot.height > 0 }
                missingThumbnails = thumbnails.mapNotNull { node ->
                    val id = node.config[SemanticsProperties.TestTag].removePrefix("tab_thumbnail:")
                    if (id in fixtureIds && compose.onAllNodesWithTag("tab_thumbnail_loaded:$id", useUnmergedTree = true)
                            .fetchSemanticsNodes().isEmpty()) "$id at ${node.boundsInRoot}" else null
                }
                missingThumbnails.isEmpty()
            }
        } catch (error: AssertionError) {
            throw AssertionError("$name: missing thumbnails $missingThumbnails", error)
        }
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.cacheDir, name).outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    private fun fixturePreview(title: String, index: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(246, 244, 238))
        paint.color = Color.rgb(30 + index * 10, 94 + index * 7, 75 + index * 9)
        canvas.drawRect(0f, 0f, 600f, 360f, paint)
        paint.color = Color.WHITE
        paint.textSize = 24f
        canvas.drawText("FIELD NOTES / 0${index + 1}", 36f, 70f, paint)
        paint.textSize = 38f
        canvas.drawText(title, 36f, 290f, paint)
        paint.color = Color.rgb(62, 70, 63)
        paint.textSize = 22f
        canvas.drawText("A little inspiration for your day.", 36f, 420f, paint)
        paint.color = Color.rgb(210, 216, 204)
        for (row in 0..7) canvas.drawRect(36f, 465f + row * 38f, if (row == 7) 380f else 560f, 477f + row * 38f, paint)
        return bitmap
    }
}
