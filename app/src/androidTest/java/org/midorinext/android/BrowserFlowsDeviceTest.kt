package org.midorinext.android

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.feature.pwa.intent.WebAppIntentProcessor
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.midorinext.android.ext.defaultBrowserStatus
import org.midorinext.android.intent.IntentReceiverActivity
import org.midorinext.android.newtab.NATIVE_HOME_URL
import org.midorinext.android.preferences.app.*

@RunWith(AndroidJUnit4::class)
class BrowserFlowsDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: AppPreferences
    private var originalIds = emptySet<String>()
    private var profileCaptured = false
    private var originalSelection: String? = null
    private var onboarding = false
    private var home = ""

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        preferences = app.appPreferencesRepository.get().flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        onboarding = shared.getBoolean(key, true)
        shared.edit().putBoolean(key, false).commit()
        app.appPreferencesRepository.get().apply {
            updateToolbarPosition(ToolbarPosition.TOP)
            updateTabsView(TabsViewOption.GRID)
            updateCloseTabsOnExit(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        originalIds = app.store.get().state.tabs.mapTo(hashSetOf()) { it.id }
        originalSelection = app.store.get().state.selectedTabId
        profileCaptured = true
        instrumentation.runOnMainSync {
            home = app.tabsUseCases.get().addTab(NATIVE_HOME_URL, selectTab = true, startLoading = false)
        }
        awaitBrowser()
    }

    @After
    fun restore(): Unit = runBlocking {
        if (!profileCaptured) {
            if (::scenario.isInitialized) scenario.close()
            return@runBlocking
        }
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            app.store.get().state.tabs.filterNot { it.id in originalIds }.forEach {
                app.store.get().dispatch(TabListAction.RemoveTabAction(it.id))
            }
        }
        compose.waitUntil(10_000) { app.store.get().state.tabs.all { it.id in originalIds } }
        assertEquals(originalIds, app.store.get().state.tabs.mapTo(hashSetOf()) { it.id })
        assertTrue("Persist the original tabs after cleanup", app.sessionStorage.get().save(app.store.get().state))
        app.appPreferencesRepository.get().apply {
            updateToolbarPosition(preferences.toolbarPosition)
            updateTabsView(preferences.tabsView)
        }
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboarding).commit()
        scenario.close()
        app.appPreferencesRepository.get().updateCloseTabsOnExit(preferences.closeTabsOnExit)
    }

    @Test
    fun onboardingLegalCancellationConfirmationAndSkipUseRealResults() {
        runBlocking { app.appPreferencesRepository.get().updateToolbarPosition(ToolbarPosition.BOTTOM) }
        val roles = shell("dumpsys role")
        require(roles.contains("name=android.app.role.BROWSER"))
        val holders = Regex("holders=([^\\s}]+)").find(roles.substringAfter("name=android.app.role.BROWSER").substringBefore('}'))
            ?.groupValues?.get(1)?.split(',').orEmpty()
        try {
            shell("cmd role remove-role-holder --user 0 android.app.role.BROWSER ${app.packageName}")
            scenario.close()
            PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_show_onboarding), true).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            compose.onNodeWithTag("onboarding_terms").performScrollTo().performClick()
            compose.onNodeWithTag("onboarding_legal").assertIsDisplayed()
            back()
            compose.onNodeWithTag("onboarding_privacy").performScrollTo().performClick()
            compose.onNodeWithTag("onboarding_legal").assertIsDisplayed()
            scenario.recreate()
            compose.onNodeWithTag("onboarding_legal").assertIsDisplayed()
            back()
            val before = app.store.get().state.tabs.map { it.id }
            compose.onNodeWithTag("onboarding_primary").performClick()
            compose.onNodeWithText(app.getString(R.string.onboarding_default_title)).assertIsDisplayed()
            compose.onNodeWithTag("onboarding_primary").performClick()
            SystemClock.sleep(700)
            back()
            compose.onNodeWithText(app.getString(R.string.onboarding_default_title)).assertIsDisplayed()
            compose.onNodeWithText(app.getString(R.string.onboarding_action_not_completed)).assertIsDisplayed()
            assertFalse(app.defaultBrowserStatus().isDefault)
            shell("cmd role add-role-holder --user 0 android.app.role.BROWSER ${app.packageName}")
            scenario.recreate()
            assertTrue(app.defaultBrowserStatus().isDefault)
            compose.onNodeWithText(app.getString(R.string.onboarding_default_done)).assertIsDisplayed()
            compose.onNodeWithTag("onboarding_primary").performClick()
            if (compose.onAllNodesWithTag("onboarding_skip").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithTag("onboarding_primary").performClick()
                SystemClock.sleep(700)
                back()
                compose.onNodeWithTag("onboarding_skip").performClick()
            }
            compose.onNodeWithText(app.getString(R.string.onboarding_toolbar_bottom)).assertIsSelected()
            repeat(3) { compose.onNodeWithTag("onboarding_primary").performClick() }
            awaitBrowser()
            assertEquals(before, app.store.get().state.tabs.map { it.id })
            capture("onboarding-finished")
        } finally {
            shell("cmd role remove-role-holder --user 0 android.app.role.BROWSER ${app.packageName}")
            holders.forEach { shell("cmd role add-role-holder --user 0 android.app.role.BROWSER $it") }
        }
    }

    @Test
    fun trayPrivacySearchSelectionRestoreAndIntentsKeepOneBrowser() {
        val privateId = compose.runOnUiThread {
            app.tabsUseCases.get().addTab(NATIVE_HOME_URL, private = true, selectTab = true, startLoading = false)
        }
        val closedBefore = app.store.get().state.undoHistory.tabs.size
        openTray()
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        scenario.recreate()
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        predictiveBack(cancel = true)
        compose.onNodeWithTag("tabs_page_PRIVATE").assertIsSelected()
        assertEquals(privateId, app.store.get().state.selectedTabId)
        predictiveBack(cancel = false)
        awaitBrowser()
        compose.onNodeWithTag("tabs_screen").assertDoesNotExist()
        assertEquals(privateId, app.store.get().state.selectedTabId)
        openTray()
        compose.onNodeWithTag("tabs_page_NORMAL").performClick()
        assertEquals(privateId, app.store.get().state.selectedTabId)
        compose.onNodeWithContentDescription(app.getString(R.string.browser_search_tabs)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("unmatched integration query")
        compose.onNodeWithTag("tabs_page_PRIVATE").performClick()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        awaitIme(visible = false)
        compose.onNodeWithTag("tab_card:$privateId").performTouchInput { longClick() }
        compose.onNodeWithTag("tabs_selection_menu").assertExists()
        compose.onNodeWithTag("tabs_page_NORMAL").performClick()
        compose.onNodeWithTag("tabs_selection_menu").assertDoesNotExist()
        compose.onNodeWithTag("tabs_page_PRIVATE").performClick()
        compose.onNodeWithTag("tab_card:$privateId").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { app.store.get().state.tabs.none { it.id == privateId } }
        assertFalse(app.store.get().state.selectedTab?.content?.private == true)
        assertEquals(closedBefore, app.store.get().state.undoHistory.tabs.size)
        back()
        awaitBrowser()

        val requests = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/integration-view")),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.org/integration-send"),
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "midori integration shared words"),
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, "midori integration search words"),
            Intent(WebAppIntentProcessor.ACTION_VIEW_PWA, Uri.parse("https://example.org/integration-pwa-fallback")),
        )
        for ((index, request) in requests.withIndex()) {
            if (index % 2 == 0) openTray() else {
                openMenu()
                compose.onNodeWithTag("browser_menu_settings").performScrollTo().performClick()
            }
            val before = app.store.get().state.tabs.size
            val previous = app.store.get().state.selectedTabId
            request.setClass(app, IntentReceiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(request)
            compose.waitUntil(30_000) { app.store.get().state.tabs.size == before + 1 && app.store.get().state.selectedTabId != previous }
            awaitBrowser()
            assertFalse(app.store.get().state.selectedTab!!.content.private)
            assertTrue(app.store.get().state.selectedTab!!.source is SessionState.Source.External)
            val selected = app.store.get().state.selectedTabId
            scenario.recreate()
            awaitBrowser()
            assertEquals(before + 1, app.store.get().state.tabs.size)
            assertEquals(selected, app.store.get().state.selectedTabId)
        }
        verifyInstalledPwaUsesOneDocumentTask()
        capture("tray-intents-finished")
    }

    @Test
    fun menuKeepsOrderFocusAndGlobalProtectionAcrossToolbarPositions() = runBlocking {
        for (position in listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM)) {
            app.appPreferencesRepository.get().updateToolbarPosition(position)
            openMenu()
            compose.onNodeWithTag("browser_menu_find").assertDoesNotExist()
            compose.onNodeWithTag("browser_menu_bookmark_page").assertDoesNotExist()
            compose.onNodeWithTag("browser_menu_protection").performScrollTo().performClick()
            compose.onNodeWithText(app.getString(R.string.adblock_settings_title)).assertExists()
            back()
            awaitBrowser()
            openMenu()
            back()
            compose.onNodeWithTag("browser_menu_button").assertIsFocused()
            openMenu()
            compose.onNodeWithTag("browser_menu_downloads").performScrollTo().assertIsDisplayed().performClick()
            back()
            awaitBrowser()
            openMenu()
            compose.onNodeWithTag("browser_menu_settings").performScrollTo().assertIsDisplayed()
            capture("menu-${position.name.lowercase()}")
            compose.onNodeWithTag("browser_menu_settings").performClick()
            back()
            awaitBrowser()
        }
    }

    @Test
    fun backExitsFullscreenThenWebHistoryAndKeyboardWithoutClosingTheTab() {
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.getInputStream().bufferedReader().readLine()
                        val body = """<meta name="viewport" content="width=device-width,initial-scale=1"><title>Fullscreen integration</title><button style="position:fixed;inset:0;font-size:32px" onclick="document.documentElement.requestFullscreen()">Fullscreen</button>""".toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        try {
            instrumentation.runOnMainSync { app.sessionUseCases.get().loadUrl("http://127.0.0.1:${server.localPort}/fullscreen", sessionId = home) }
            compose.waitUntil(30_000) { app.store.get().state.selectedTab?.content?.let { it.title == "Fullscreen integration" && !it.loading } == true }
            val bounds = android.graphics.Rect()
            scenario.onActivity { activity -> activity.window.decorView.getWindowVisibleDisplayFrame(bounds) }
            val down = SystemClock.uptimeMillis()
            instrumentation.sendPointerSync(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
            instrumentation.sendPointerSync(MotionEvent.obtain(down, down + 100, MotionEvent.ACTION_UP, bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0))
            compose.waitUntil(15_000) { app.store.get().state.selectedTab?.content?.fullScreen == true }
            back()
            compose.waitUntil(10_000) { app.store.get().state.selectedTab?.content?.fullScreen == false }
            assertEquals(home, app.store.get().state.selectedTabId)
            instrumentation.runOnMainSync { app.sessionUseCases.get().loadUrl("http://127.0.0.1:${server.localPort}/next", sessionId = home) }
            compose.waitUntil(15_000) { app.store.get().state.selectedTab?.content?.url?.endsWith("/next") == true && app.store.get().state.selectedTab?.content?.canGoBack == true }
            back()
            compose.waitUntil(15_000) { app.store.get().state.selectedTab?.content?.url?.endsWith("/fullscreen") == true }
            openMenu()
            compose.onNodeWithTag("browser_menu_find").performScrollTo().assertIsDisplayed()
            back()
            compose.onNodeWithTag("browser_menu_button").assertIsFocused()
            compose.onNodeWithText("127.0.0.1", substring = true).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("unsubmitted integration text")
            awaitIme(visible = true)
            back()
            awaitIme(visible = false)
            compose.onNode(hasSetTextAction()).assertTextEquals("unsubmitted integration text")
            back()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
            compose.onNode(hasSetTextAction()).assertDoesNotExist()
            assertTrue(app.store.get().state.selectedTab!!.content.url.endsWith("/fullscreen"))
            assertEquals(home, app.store.get().state.selectedTabId)
            capture("fullscreen-history-finished")
        } finally {
            server.close()
            executor.shutdownNow()
        }
    }

    private fun back() { compose.waitForIdle(); shell("input keyevent ${KeyEvent.KEYCODE_BACK}"); compose.waitForIdle() }
    private fun awaitIme(visible: Boolean) {
        compose.waitUntil(10_000) {
            var shown: Boolean? = null
            scenario.onActivity {
                shown = androidx.core.view.ViewCompat.getRootWindowInsets(it.window.decorView)
                    ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())
            }
            shown == visible
        }
    }
    private fun predictiveBack(cancel: Boolean) {
        scenario.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackStarted(androidx.activity.BackEventCompat(0f, 100f, 0f, androidx.activity.BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        scenario.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackProgressed(androidx.activity.BackEventCompat(300f, 100f, .6f, androidx.activity.BackEventCompat.EDGE_LEFT))
        }
        compose.waitForIdle()
        scenario.onActivity {
            if (cancel) it.onBackPressedDispatcher.dispatchOnBackCancelled()
            else it.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }
    private fun verifyInstalledPwaUsesOneDocumentTask() = runBlocking {
        val url = "https://example.org/integration-installed-pwa"
        val database = androidx.room.Room.databaseBuilder(app, org.midorinext.android.pwa.InstalledWebAppDatabase::class.java, "installed_web_apps.db").build()
        val storage = mozilla.components.feature.pwa.ManifestStorage(app)
        val manifest = mozilla.components.concept.engine.manifest.WebAppManifest(
            name = "Midori integration PWA", startUrl = url,
            display = mozilla.components.concept.engine.manifest.WebAppManifest.DisplayMode.STANDALONE,
        )
        val before = app.store.get().state.tabs.map { it.id }
        try {
            storage.saveManifest(manifest)
            database.apps().put(org.midorinext.android.pwa.InstalledWebApp(url, manifest.name, "https://example.org/", "STANDALONE", System.currentTimeMillis()))
            repeat(2) {
                app.startActivity(Intent(WebAppIntentProcessor.ACTION_VIEW_PWA, Uri.parse(url)).setClass(app, IntentReceiverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                compose.waitUntil(20_000) {
                    var visible = false
                    instrumentation.runOnMainSync {
                        visible = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                            .any { it is org.midorinext.android.pwa.WebAppActivity && it.intent.dataString == url }
                    }
                    visible
                }
                assertEquals(1, app.getSystemService(android.app.ActivityManager::class.java).appTasks.count { it.taskInfo?.baseIntent?.dataString == url })
                assertEquals(before, app.store.get().state.tabs.map { it.id })
            }
            shell("input keyevent ${KeyEvent.KEYCODE_BACK}")
            awaitBrowser()
        } finally {
            instrumentation.runOnMainSync {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filter { it is org.midorinext.android.pwa.WebAppActivity && it.intent.dataString == url }.forEach { it.finish() }
            }
            database.apps().delete(url)
            storage.removeManifests(listOf(url))
            database.close()
        }
    }
    private fun openMenu() { compose.onNodeWithContentDescription(app.getString(R.string.nav_menu)).performClick(); compose.onNodeWithTag("browser_menu_sheet").assertIsDisplayed() }
    private fun openTray() {
        val state = app.store.get().state
        val private = state.selectedTab?.content?.private == true
        val count = state.tabs.count { it.content.private == private }
        val description = if (count == 1) app.getString(R.string.tab_tray_title_single) else app.getString(R.string.tab_tray_title, count)
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(description).performClick()
        compose.onNodeWithTag("tabs_screen").assertIsDisplayed()
    }
    private fun awaitBrowser() {
        compose.waitUntil(30_000) {
            try {
                compose.onAllNodesWithContentDescription(app.getString(R.string.nav_menu)).fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }
        compose.waitForIdle()
    }
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText() }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(app.cacheDir, "p19-p21-$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
