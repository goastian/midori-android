package org.midorinext.android.ui.browser

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.MainActivity
import org.midorinext.android.MidoriApplication
import org.midorinext.android.R
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.ToolbarPosition
import java.io.File

@RunWith(AndroidJUnit4::class)
class NativeBrowserUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: AppPreferences
    private var originalSelection: String? = null
    private var fixtureId: String? = null
    private var onboardingEnabled = false

    @Before
    fun prepare() = runBlocking {
        preferences = app.appPreferencesRepository.get().flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        onboardingEnabled = shared.getBoolean(key, true)
        shared.edit().putBoolean(key, false).commit()
        app.appPreferencesRepository.get().updateAppearance(Appearance.LIGHT)
        app.appPreferencesRepository.get().updateToolbarPosition(ToolbarPosition.TOP)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        originalSelection = app.store.get().state.selectedTabId
        instrumentation.runOnMainSync {
            fixtureId = app.tabsUseCases.get().addTab("about:blank", selectTab = true, private = false)
        }
        compose.waitForIdle()
    }

    @After
    fun restore() = runBlocking {
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            fixtureId?.let { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
        }
        app.appPreferencesRepository.get().updateToolbarPosition(preferences.toolbarPosition)
        app.appPreferencesRepository.get().updateAppearance(preferences.appearance)
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboardingEnabled).commit()
        scenario.close()
    }

    @Test
    fun menuGeometryAndOrderAreIndependentOfToolbarPosition(): Unit = runBlocking {
        openMenu()
        val top = menuBounds()
        capture("menu-toolbar-top.png")
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("browser_menu_sheet").assertDoesNotExist()

        app.appPreferencesRepository.get().updateToolbarPosition(ToolbarPosition.BOTTOM)
        compose.waitForIdle()
        openMenu()
        val bottom = menuBounds()
        top.zip(bottom).forEach { (first, second) ->
            assertEquals(first.left, second.left, 1f)
            assertEquals(first.top, second.top, 1f)
            assertEquals(first.width, second.width, 1f)
            assertEquals(first.height, second.height, 1f)
        }
        capture("menu-toolbar-bottom.png")
        val library = bottom.slice(2..5)
        assertTrue(library.zipWithNext().all { (first, second) -> first.left < second.left })
        assertTrue(library.all { it.top == library.first().top })
        assertTrue(bottom[0].top < bottom[1].top && bottom[1].top < library.first().top)
        compose.onNodeWithTag("browser_menu_settings").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun openMenuSurvivesActivityRecreationAndDismissesByGesture() {
        openMenu()
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("browser_menu_sheet").assertIsDisplayed()
        compose.onNodeWithTag("browser_menu_sheet").performTouchInput { swipeDown(durationMillis = 300) }
        compose.waitForIdle()
        compose.onNodeWithTag("browser_menu_sheet").assertDoesNotExist()
    }

    @Test
    fun selectingSettingsClosesMenuAndNavigates() {
        openMenu()
        compose.onNodeWithTag("browser_menu_settings").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("browser_menu_sheet").assertDoesNotExist()
        compose.onNodeWithText(app.getString(R.string.default_browser_label)).assertExists()
    }

    private fun openMenu() {
        compose.onNodeWithContentDescription(app.getString(R.string.nav_menu)).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("browser_menu_sheet").assertIsDisplayed()
    }

    private fun menuBounds(): List<Rect> = listOf(
        "browser_menu_vpn", "browser_menu_extensions", "browser_menu_history", "browser_menu_bookmarks",
        "browser_menu_downloads", "browser_menu_passwords", "browser_menu_customize_homepage", "browser_menu_settings",
    ).map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }

    private fun capture(name: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(app.cacheDir, name).outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}
