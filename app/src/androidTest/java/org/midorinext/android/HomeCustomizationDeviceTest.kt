package org.midorinext.android

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.newtab.HomeSection
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.LEGACY_NEW_TAB_URL
import org.midorinext.android.newtab.NATIVE_HOME_URL
import org.midorinext.android.newtab.homeSiteCategories
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.HomeShortcutShape
import org.midorinext.android.preferences.app.ToolbarPosition

@RunWith(AndroidJUnit4::class)
class HomeCustomizationDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private val repository get() = app.appPreferencesRepository.get()
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var original: AppPreferences
    private var originalSelection: String? = null
    private var onboardingEnabled = false
    private var homeId: String? = null
    private val fixturePrefix = "Home customization ${UUID.randomUUID()}"
    private val fixtureIds = mutableSetOf<String>()
    private var pendingCategoryId: String? = null

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue("Device checks require the Debug variant", BuildConfig.DEBUG)
        original = repository.flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val onboardingKey = app.getString(R.string.pref_key_show_onboarding)
        onboardingEnabled = shared.getBoolean(onboardingKey, true)
        shared.edit().putBoolean(onboardingKey, false).commit()
        repository.apply {
            updateAppearance(Appearance.LIGHT)
            updateToolbarPosition(ToolbarPosition.TOP)
            updateOpenBlankNewTab(false)
            updateHomeWallpaper(HomeWallpaper.NONE)
            HomeSection.entries.forEach { updateHomeSection(it, it == HomeSection.SHORTCUTS) }
            updateHomeShortcutShape(HomeShortcutShape.HOME_SHORTCUT_ROUNDED)
            updateHomeLargeShortcuts(false)
            updateHomeAddShortcut(true)
            updateHomeLogo(true)
            updateHomeCustomizeShortcut(true)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(app.getString(R.string.nav_menu)).fetchSemanticsNodes().isNotEmpty()
        }
        instrumentation.runOnMainSync {
            originalSelection = app.store.get().state.selectedTabId
            homeId = app.tabsUseCases.get().addTab(LEGACY_NEW_TAB_URL, selectTab = true, private = false)
        }
        compose.waitUntil(30_000) {
            app.store.get().state.tabs.firstOrNull { it.id == homeId }?.let {
                it.content.url == NATIVE_HOME_URL && it.engineState.engineSession != null && !it.content.loading
            } == true
        }
        waitForTag("native_home")
        waitForPreferences { it.homeShortcutsInitialized }
        if (repository.flow.first().homeShortcutsList.none { it.parentId.isEmpty() }) {
            val id = UUID.randomUUID().toString()
            fixtureIds += id
            repository.saveHomeShortcut(id, "$fixturePrefix appearance", "https://example.org/home-customization-appearance")
        }
    }

    @After
    fun restore(): Unit = runBlocking {
        if (!::original.isInitialized) return@runBlocking
        val current = repository.flow.first().homeShortcutsList
        val originalIds = original.homeShortcutsList.map { it.id }.toSet()
        pendingCategoryId?.let { categoryId ->
            val category = homeSiteCategories.first { it.id == categoryId }
            current.filter { it.isFolder && it.id !in originalIds && it.title == app.getString(category.title) }
                .forEach { folder ->
                    fixtureIds += folder.id
                    fixtureIds += current.filter { it.parentId == folder.id }.map { it.id }
                }
        }
        fixtureIds += current.filter { it.title.startsWith(fixturePrefix) }.map { it.id }
        fixtureIds.filterNot { it in originalIds }.forEach { repository.deleteHomeShortcut(it) }
        repository.apply {
            updateAppearance(original.appearance)
            updateToolbarPosition(original.toolbarPosition)
            updateOpenBlankNewTab(original.openBlankNewTab)
            updateHomeWallpaper(HomeWallpaper.fromId(original.homeWallpaperId))
            updateHomeSection(HomeSection.SHORTCUTS, !original.hideHomeShortcuts)
            updateHomeSection(HomeSection.CONTINUE, !original.hideHomeContinue)
            updateHomeSection(HomeSection.RECENT_VISITS, !original.hideHomeRecentVisits)
            updateHomeSection(HomeSection.STORIES, !original.hideHomeStories)
            updateHomeSection(HomeSection.PRIVACY, !original.hideHomePrivacy)
            updateHomeShortcutShape(original.homeShortcutShape)
            updateHomeLargeShortcuts(original.homeLargeShortcuts)
            updateHomeAddShortcut(!original.hideHomeAddShortcut)
            updateHomeLogo(!original.hideHomeLogo)
            updateHomeCustomizeShortcut(!original.hideHomeCustomizeShortcut)
        }
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboardingEnabled).commit()
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            homeId?.let { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
        }
        scenario?.close()
    }

    @Test
    fun backgroundLongPressOpensCustomizationAndAppearanceSurvivesRecreation() {
        openContextMenu()
        compose.onNodeWithTag("home_context_customize").assertTextContains(app.getString(R.string.menu_customize_homepage))
        capture("home-customization-context.png")
        compose.onNodeWithTag("home_context_customize").performClick()
        waitForTag("home_shape_circle")
        compose.onNodeWithTag("home_shape_square").performScrollTo().performClick()
        waitForPreferences { it.homeShortcutShape == HomeShortcutShape.HOME_SHORTCUT_SQUARE }
        compose.onNodeWithTag("home_shape_square").assertIsSelected()
        compose.onNodeWithTag("home_shape_circle").performClick()
        waitForPreferences { it.homeShortcutShape == HomeShortcutShape.HOME_SHORTCUT_CIRCLE }
        compose.onNodeWithTag("home_shape_circle").assertIsSelected()
        compose.onNodeWithTag("home_large_icons").performScrollTo().performClick()
        waitForPreferences { it.homeLargeShortcuts }
        compose.onNodeWithTag("home_large_icons").assertIsOn()
        compose.onNodeWithTag("home_show_add_shortcut").performScrollTo().performClick()
        waitForPreferences { it.hideHomeAddShortcut }
        compose.onNodeWithTag("home_show_add_shortcut").assertIsOff()
        compose.onNodeWithTag("home_show_logo").performScrollTo().performClick()
        waitForPreferences { it.hideHomeLogo }
        compose.onNodeWithTag("home_show_logo").assertIsOff()
        compose.onNodeWithTag("home_show_customize_shortcut").performScrollTo().performClick()
        waitForPreferences { it.hideHomeCustomizeShortcut }
        compose.onNodeWithTag("home_show_customize_shortcut").assertIsOff()
        waitForPreferences {
            it.homeShortcutShape == HomeShortcutShape.HOME_SHORTCUT_CIRCLE && it.homeLargeShortcuts &&
                it.hideHomeAddShortcut && it.hideHomeLogo && it.hideHomeCustomizeShortcut
        }
        capture("home-customization-settings.png")
        compose.onNodeWithContentDescription(app.getString(R.string.nav_back)).performClick()
        waitForTag("native_home")
        scenario!!.recreate()
        waitForTag("native_home")
        compose.onNodeWithTag("native_home_brand").assertDoesNotExist()
        compose.onNodeWithTag("native_home_customize").assertDoesNotExist()
        compose.onNodeWithTag("home_add_shortcut").assertDoesNotExist()
        val icons = compose.onAllNodesWithTag("home_shortcut_icon", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(icons.isNotEmpty())
        icons.forEach {
            assertEquals(64f * app.resources.displayMetrics.density, it.boundsInRoot.width, 1f)
        }
        capture("home-customization-persisted.png")

        openContextMenu()
        compose.onNodeWithTag("home_context_customize").performClick()
        waitForTag("home_shape_circle")
        compose.onNodeWithTag("home_shape_circle").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("home_large_icons").performScrollTo().assertIsOn()
        compose.onNodeWithTag("home_show_add_shortcut").performScrollTo().assertIsOff()
        compose.onNodeWithTag("home_show_logo").performScrollTo().assertIsOff()
        compose.onNodeWithTag("home_show_customize_shortcut").performScrollTo().assertIsOff()
    }

    @Test
    fun foldersSupportAddingMovingRenamingAndRemovingWithoutLosingSites(): Unit = runBlocking {
        val folderTitle = "$fixturePrefix folder"
        openContextMenu()
        compose.onNodeWithTag("home_context_add_folder").performClick()
        compose.onNodeWithTag("home_folder_name").performTextInput(folderTitle)
        compose.onNodeWithTag("home_folder_save").performClick()
        waitForTagGone("home_folder_save")
        val folder = repository.flow.first().homeShortcutsList.single { it.title == folderTitle }
        fixtureIds += folder.id
        openFolder(folder.id)
        compose.onNodeWithText(app.getString(R.string.home_folder_empty)).assertIsDisplayed()
        addSiteInOpenFolder("$fixturePrefix moved", "https://example.org/home-customization-moved")
        val child = repository.flow.first().homeShortcutsList.single { it.title == "$fixturePrefix moved" }
        fixtureIds += child.id
        assertEquals(folder.id, child.parentId)
        scenario!!.recreate()
        waitForTag("native_home")
        openFolder(folder.id)
        collectionItem(child.id).assertIsDisplayed().performTouchInput { longClick() }
        compose.onNodeWithText(app.getString(R.string.home_move_to_folder)).performClick()
        compose.onNodeWithTag("home_move_target:").performClick()
        waitForPreferences { prefs -> prefs.homeShortcutsList.single { it.id == child.id }.parentId.isEmpty() }
        compose.onNodeWithText(app.getString(R.string.home_folder_empty)).assertIsDisplayed()

        compose.onNodeWithContentDescription(app.getString(R.string.home_edit_folder)).performClick()
        compose.onNodeWithText(app.getString(R.string.home_edit_folder)).performClick()
        compose.onNodeWithTag("home_folder_name").performTextReplacement("$fixturePrefix renamed")
        compose.onNodeWithTag("home_folder_save").performClick()
        waitForTagGone("home_folder_save")
        compose.onNodeWithText("$fixturePrefix renamed").assertIsDisplayed()
        addSiteInOpenFolder("$fixturePrefix retained", "https://example.org/home-customization-retained")
        val retained = repository.flow.first().homeShortcutsList.single { it.title == "$fixturePrefix retained" }
        fixtureIds += retained.id
        openFolder(folder.id)
        capture("home-customization-folder.png")
        compose.onNodeWithContentDescription(app.getString(R.string.home_edit_folder)).performClick()
        compose.onNodeWithText(app.getString(R.string.home_remove_folder)).performClick()
        compose.onNodeWithText(app.getString(R.string.home_remove_folder_message)).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.adblock_remove)).performClick()
        waitForPreferences { prefs -> prefs.homeShortcutsList.none { it.id == folder.id } }
        waitForTagGone("home_shortcuts_sheet")
        val remaining = repository.flow.first().homeShortcutsList
        assertTrue(remaining.single { it.id == child.id }.parentId.isEmpty())
        assertTrue(remaining.single { it.id == retained.id }.parentId.isEmpty())
        assertEquals(original.homeShortcutsList, remaining.filter { site -> original.homeShortcutsList.any { it.id == site.id } })
        compose.onNodeWithText(app.getString(R.string.home_show_all)).performClick()
        collectionItem(retained.id).assertIsDisplayed()
    }

    @Test
    fun aSiteCategoryCanBeAddedAsAFolderWithAllItsSites(): Unit = runBlocking {
        val category = homeSiteCategories.first { it.id == "gaming" }
        val existingIds = repository.flow.first().homeShortcutsList.map { it.id }.toSet()
        compose.onNodeWithTag("home_add_shortcut").performClick()
        waitForTag("home_site_picker")
        capture("home-customization-picker.png")
        compose.onNodeWithTag("home_category:${category.id}").performScrollTo().performClick()
        pendingCategoryId = category.id
        compose.onNodeWithTag("home_add_category_folder").performScrollTo().performClick()
        waitForTagGone("home_site_picker")
        val added = repository.flow.first().homeShortcutsList.filter { it.id !in existingIds }
        fixtureIds += added.map { it.id }
        val folder = added.single { it.isFolder }
        assertEquals(app.getString(category.title), folder.title)
        assertTrue(folder.parentId.isEmpty())
        assertEquals(category.sites.map { it.url }, added.filter { it.parentId == folder.id }.map { it.url })
        scenario!!.recreate()
        waitForTag("native_home")
        openFolder(folder.id)
        added.filter { it.parentId == folder.id }.forEach { site ->
            collectionItem(site.id).assertIsDisplayed().assertTextContains(site.title)
        }
        compose.onNode(hasText(app.getString(R.string.home_add_shortcut)) and hasAnyAncestor(hasTestTag("home_shortcuts_sheet")))
            .assertIsDisplayed()
        capture("home-customization-category.png")
    }

    private fun openContextMenu() {
        compose.onNodeWithTag("native_home").performScrollToIndex(0)
        compose.onNodeWithTag("native_home").performTouchInput { longClick(Offset(center.x, 6f)) }
        waitForTag("home_context_menu")
        compose.onNodeWithTag("home_context_menu").assertIsDisplayed()
    }

    private fun openFolder(id: String) {
        compose.onNodeWithText(app.getString(R.string.home_show_all)).performClick()
        waitForTag("home_shortcuts_sheet")
        collectionItem(id).performClick()
        compose.waitForIdle()
    }

    private fun collectionItem(id: String): SemanticsNodeInteraction {
        val tag = "home_collection_item:$id"
        compose.onNodeWithTag("home_collection_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }

    private fun addSiteInOpenFolder(title: String, url: String) {
        compose.onNode(hasText(app.getString(R.string.home_add_shortcut)) and hasAnyAncestor(hasTestTag("home_shortcuts_sheet")))
            .performClick()
        waitForTag("home_site_picker")
        compose.onNodeWithTag("home_add_website").performClick()
        compose.onNodeWithTag("home_shortcut_title").performTextInput(title)
        compose.onNodeWithTag("home_shortcut_url").performTextInput(url)
        compose.onNodeWithTag("home_shortcut_save").performClick()
        waitForTagGone("home_shortcut_save")
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForTagGone(tag: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForPreferences(predicate: (AppPreferences) -> Boolean) {
        compose.waitUntil(10_000) { runBlocking { predicate(repository.flow.first()) } }
    }

    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(app.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
