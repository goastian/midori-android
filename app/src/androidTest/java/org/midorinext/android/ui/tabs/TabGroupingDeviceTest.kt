package org.midorinext.android.ui.tabs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.base.images.ImageSaveRequest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.midorinext.android.BuildConfig
import org.midorinext.android.MainActivity
import org.midorinext.android.MidoriApplication
import org.midorinext.android.R
import org.midorinext.android.preferences.app.*

@RunWith(AndroidJUnit4::class)
class TabGroupingDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: AppPreferences
    private lateinit var viewModel: TabsScreenViewModel
    private val fixtures = mutableListOf<String>()
    private var originalSelection: String? = null
    private var onboarding = false
    private val repository get() = app.appPreferencesRepository.get()

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        preferences = repository.flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        onboarding = shared.getBoolean(key, true)
        shared.edit().putBoolean(key, false).commit()
        repository.updateAppearance(Appearance.LIGHT)
        repository.updateTabsView(TabsViewOption.GRID)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        originalSelection = app.store.get().state.selectedTabId
        scenario.onActivity { viewModel = ViewModelProvider(it)[TabsScreenViewModel::class.java] }
        for (index in 1..4) {
            val url = "https://group-gesture-$index.example/"
            instrumentation.runOnMainSync {
                val id = app.tabsUseCases.get().addTab(url, title = "Drag tab $index", selectTab = false, startLoading = false)
                fixtures += id
                val icon = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(0, 162, 102)) }
                app.store.get().dispatch(ContentAction.UpdateIconAction(id, url, icon))
            }
            val bitmap = Bitmap.createBitmap(320, 460, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                drawColor(Color.rgb(230 - index * 12, 242 - index * 6, 235))
                drawText("Tab $index", 28f, 80f, Paint().apply { color = Color.rgb(20, 90, 65); textSize = 36f; isAntiAlias = true })
            }
            viewModel.thumbnailStorage.saveThumbnail(ImageSaveRequest(fixtures.last(), false), bitmap).join()
            bitmap.recycle()
        }
        val count = app.store.get().state.tabs.count { !it.content.private }
        val description = app.getString(R.string.tab_tray_title, count)
        compose.waitUntil(20_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tabs_grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tabs_grid").performScrollToIndex(0)
    }

    @After
    fun restore(): Unit = runBlocking {
        compose.mainClock.autoAdvance = true
        if (::preferences.isInitialized) {
            repository.updateTabGroupsAndColors { _, _ -> preferences.tabGroupsList to preferences.tabGroupColorsMap }
            repository.updateAppearance(preferences.appearance)
            repository.updateTabsView(preferences.tabsView)
        }
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            fixtures.forEach { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
        }
        compose.waitUntil(10_000) { app.store.get().state.tabs.none { it.id in fixtures } }
        app.sessionStorage.get().save(app.store.get().state)
        fixtures.forEach { viewModel.thumbnailStorage.deleteThumbnail(it, false).join() }
        for (index in 1..4) app.historyRepository.get().deleteVisitsFor("https://group-gesture-$index.example/")
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboarding).commit()
        compose.waitForIdle()
        if (::scenario.isInitialized) scenario.close()
    }

    @Test
    fun gridDropCreatesAnInlineGroupAndUndoKeepsEveryTab(): Unit = runBlocking {
        val before = app.store.get().state.tabs.map { it.id }
        val selected = app.store.get().state.selectedTabId
        drag(fixtures[3], "tab_card:${fixtures[2]}")
        val group = awaitGroup(setOf(fixtures[2], fixtures[3]))
        compose.onNodeWithTag("tab_group_expanded:${group.id}").assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.browser_name_tab_group)).assertDoesNotExist()
        assertEquals(before, app.store.get().state.tabs.map { it.id })
        assertEquals(selected, app.store.get().state.selectedTabId)
        capture("tab-groups-grid-created.png")
        undo()
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList == preferences.tabGroupsList } }
        assertEquals(before, app.store.get().state.tabs.map { it.id })
    }

    @Test
    fun leavingTheTargetOrCancellingDoesNotGroupOrCloseTabs(): Unit = runBlocking {
        val before = app.store.get().state.tabs.map { it.id }
        drag(fixtures[3], "tab_card:${fixtures[2]}", leaveTarget = true)
        assertEquals(preferences.tabGroupsList, repository.flow.first().tabGroupsList)
        assertEquals(before, app.store.get().state.tabs.map { it.id })
        drag(fixtures[3], "tab_card:${fixtures[2]}", cancel = true)
        assertEquals(preferences.tabGroupsList, repository.flow.first().tabGroupsList)
        assertEquals(before, app.store.get().state.tabs.map { it.id })
    }

    @Test
    fun droppingOnAnExistingGroupAddsTabsAndUndoRestoresIt(): Unit = runBlocking {
        val change = viewModel.dropTabs(setOf(fixtures[3]), TabDropTarget.Tab(fixtures[2]), "Reference group")!!
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab_group:${change.groupId}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tabs_grid").performScrollToIndex(0)
        drag(fixtures[1], "tab_group:${change.groupId}")
        val group = awaitGroup(setOf(fixtures[1], fixtures[2], fixtures[3]))
        assertEquals(change.groupId, group.id)
        compose.onNodeWithTag("tab_group_expanded:${group.id}").assertIsDisplayed()
        capture("tab-groups-added.png")
        undo()
        compose.waitUntil(10_000) {
            runBlocking { repository.flow.first().tabGroupsList.firstOrNull { it.id == group.id }?.tabIdsCount == 2 }
        }
        assertTrue(app.store.get().state.tabs.any { it.id == fixtures[1] })
    }

    @Test
    fun carouselDragFollowsThePointerAndCreatesAGroupAcrossCards(): Unit = runBlocking {
        val selected = app.store.get().state.selectedTabId
        repository.updateTabsView(TabsViewOption.CAROUSEL)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tabs_carousel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(preferences.tabGroupsCount)
        val screen = compose.onNodeWithTag("tabs_screen")
        val bounds = screen.fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("tab_card:${fixtures[3]}").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
        compose.mainClock.autoAdvance = false
        var pointerDown = false
        try {
            screen.performTouchInput { down(start); advanceEventTime(650) }
            pointerDown = true
            compose.mainClock.advanceTimeBy(700)
            screen.performTouchInput { moveTo(Offset(bounds.width - 24f, start.y), delayMillis = 100) }
            compose.mainClock.advanceTimeBy(350)
            val target = compose.onNodeWithTag("tab_card:${fixtures[2]}").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
            screen.performTouchInput { moveTo(target, delayMillis = 100) }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("tab_drag_preview").assertIsDisplayed()
            capture("tab-groups-carousel-dragging.png")
            screen.performTouchInput { up() }
            pointerDown = false
        } finally {
            if (pointerDown) screen.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
        }
        val group = awaitGroup(setOf(fixtures[2], fixtures[3]))
        assertEquals(selected, app.store.get().state.selectedTabId)
        compose.onNodeWithTag("tab_group_expanded:${group.id}").assertIsDisplayed()
        capture("tab-groups-carousel-created.png")
    }

    @Test
    fun selectionMenuCanSelectAllDeselectAndMoveSeveralTabs(): Unit = runBlocking {
        val change = viewModel.dropTabs(setOf(fixtures[3]), TabDropTarget.Tab(fixtures[2]), "Reference group")!!
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tab_group:${change.groupId}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_card:${fixtures[1]}").performTouchInput { longClick() }
        compose.onNodeWithTag("tabs_selection_menu").performClick()
        compose.onNodeWithText(app.getString(R.string.tabs_select_all)).performClick()
        val count = app.store.get().state.tabs.count { !it.content.private }
        compose.onNodeWithText(app.getString(R.string.browser_group_tabs_selected, count)).assertIsDisplayed()
        compose.onNodeWithTag("tabs_selection_menu").performClick()
        compose.onNodeWithText(app.getString(R.string.tabs_deselect_all)).performClick()
        compose.onNodeWithText(app.getString(R.string.browser_group_tabs_selected, 0)).assertIsDisplayed()
        for (id in fixtures.take(2)) {
            compose.onNodeWithTag("tabs_grid").performScrollToNode(hasTestTag("tab_card:$id"))
            compose.onNodeWithTag("tab_card:$id").performClick()
        }
        compose.onNodeWithTag("tabs_selection_menu").performClick()
        compose.onNodeWithText(app.getString(R.string.tabs_move_to_group)).performClick()
        compose.onNodeWithTag("tabs_move_to_group:${change.groupId}").performClick()
        val group = awaitGroup(fixtures.toSet())
        assertEquals(change.groupId, group.id)
        undo()
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList.first { it.id == change.groupId }.tabIdsCount == 2 } }
    }

    @Test
    fun carouselGroupSupportsNewTabEditCollapseUngroupAndUndo(): Unit = runBlocking {
        val change = viewModel.dropTabs(setOf(fixtures[3]), TabDropTarget.Tab(fixtures[2]), "Reference group")!!
        repository.updateTabsView(TabsViewOption.CAROUSEL)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tabs_carousel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tabs_carousel").performScrollToIndex(0)
        compose.onNodeWithTag("tab_group:${change.groupId}").performClick()
        compose.onNodeWithTag("tab_group_expanded:${change.groupId}").assertIsDisplayed()
        val selected = app.store.get().state.selectedTabId
        val before = app.store.get().state.tabs.mapTo(hashSetOf()) { it.id }
        compose.onNodeWithTag("tab_group_new_tab:${change.groupId}").performClick()
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList.first { it.id == change.groupId }.tabIdsCount == 3 } }
        val newId = app.store.get().state.tabs.single { it.id !in before }.id
        fixtures += newId
        assertEquals(selected, app.store.get().state.selectedTabId)
        compose.onNodeWithTag("tabs_screen").assertIsDisplayed()
        compose.onNodeWithTag("tab_group_menu:${change.groupId}").performClick()
        compose.onNodeWithText(app.getString(R.string.browser_edit_tab_group)).performClick()
        compose.onNodeWithTag("tab_group_name").performTextReplacement("Trip plans")
        compose.onNodeWithTag("tab_group_color:PURPLE").performClick()
        compose.onNodeWithText(app.getString(R.string.browser_save_tab_group)).performClick()
        compose.waitUntil(10_000) {
            runBlocking {
                val saved = repository.flow.first()
                saved.tabGroupsList.first { it.id == change.groupId }.name == "Trip plans" &&
                    saved.tabGroupColorsMap[change.groupId] == TabGroupColor.PURPLE.value
            }
        }
        capture("tab-groups-carousel-expanded.png")
        compose.onNodeWithTag("tab_group_collapse:${change.groupId}").performClick()
        compose.onNodeWithTag("tab_group:${change.groupId}").assertIsDisplayed()
        capture("tab-groups-carousel-collapsed.png")
        compose.onNodeWithTag("tab_group:${change.groupId}").performClick()
        compose.onNodeWithTag("tab_group_menu:${change.groupId}").performClick()
        compose.onNodeWithText(app.getString(R.string.tabs_ungroup)).performClick()
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList.none { it.id == change.groupId } } }
        assertTrue(app.store.get().state.tabs.any { it.id == newId })
        undo()
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList.any { it.id == change.groupId && it.name == "Trip plans" && it.tabIdsCount == 3 } } }
    }

    @Test
    fun createdGroupsSurviveRecreationAndStayOutsidePrivateBrowsing(): Unit = runBlocking {
        drag(fixtures[3], "tab_card:${fixtures[2]}")
        val group = awaitGroup(setOf(fixtures[2], fixtures[3]))
        repository.updateAppearance(Appearance.DARK)
        compose.waitForIdle()
        scenario.recreate()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tab_group_expanded:${group.id}").fetchSemanticsNodes().isNotEmpty() }
        capture("tab-groups-dark.png")
        compose.onNodeWithTag("tabs_page_PRIVATE").performClick()
        compose.onNodeWithTag("tab_group:${group.id}").assertDoesNotExist()
        compose.onNodeWithTag("tab_group_expanded:${group.id}").assertDoesNotExist()
        assertEquals(group, repository.flow.first().tabGroupsList.first { it.id == group.id })
        compose.onNodeWithTag("tabs_page_NORMAL").performClick()
        compose.onNodeWithTag("tab_group_expanded:${group.id}").assertIsDisplayed()
    }

    private fun drag(sourceId: String, targetTag: String, leaveTarget: Boolean = false, cancel: Boolean = false) {
        val screen = compose.onNodeWithTag("tabs_screen")
        val origin = screen.fetchSemanticsNode().boundsInRoot.topLeft
        val start = compose.onNodeWithTag("tab_card:$sourceId").fetchSemanticsNode().boundsInRoot.center - origin
        val target = compose.onNodeWithTag(targetTag).fetchSemanticsNode().boundsInRoot.center - origin
        compose.mainClock.autoAdvance = false
        var pointerDown = false
        try {
            screen.performTouchInput { down(start); advanceEventTime(650) }
            pointerDown = true
            compose.mainClock.advanceTimeBy(700)
            screen.performTouchInput {
                for (step in 1..20) moveTo(start + (target - start) * (step / 20f), delayMillis = 25)
            }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("tab_drag_preview").assertIsDisplayed()
            if (!leaveTarget && !cancel) capture("tab-groups-dragging.png")
            if (leaveTarget) {
                screen.performTouchInput {
                    val outside = Offset(start.x, 24f)
                    for (step in 1..16) moveTo(target + (outside - target) * (step / 16f), delayMillis = 25)
                }
                compose.mainClock.advanceTimeBy(32)
            }
            screen.performTouchInput { if (cancel) cancel() else up() }
            pointerDown = false
        } catch (failure: Throwable) {
            File(app.cacheDir, "tab-group-gesture-failure.txt").writeText(failure.stackTraceToString())
            throw failure
        } finally {
            if (pointerDown) screen.performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
    }

    private fun awaitGroup(ids: Set<String>): SavedTabGroup {
        compose.waitUntil(10_000) { runBlocking { repository.flow.first().tabGroupsList.any { it.tabIdsList.toSet() == ids } } }
        return runBlocking { repository.flow.first().tabGroupsList.first { it.tabIdsList.toSet() == ids } }
    }

    private fun undo() {
        compose.onNodeWithText(app.getString(R.string.tabs_group_undo)).performClick()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            var focused = false
            scenario.onActivity { focused = it.hasWindowFocus() }
            focused
        }
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(app.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
