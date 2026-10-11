package org.midorinext.android

import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.midorinext.android.newtab.*
import org.midorinext.android.preferences.app.*
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.withTimeout
import org.midorinext.android.ui.browser.home.NativeHomeViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import mozilla.components.concept.engine.EngineSession
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.io.IOException
import kotlinx.coroutines.delay

@RunWith(AndroidJUnit4::class)
class NativeHomeDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var preferences: AppPreferences
    private val fixtures = mutableListOf<String>()
    private var originalSelection: String? = null
    private var onboardingEnabled = false
    private var homeId = ""
    private val historyUrl = "https://native-home-fixture.example/"

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue("Device checks require the Debug variant", BuildConfig.DEBUG)
        preferences = app.appPreferencesRepository.get().flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        onboardingEnabled = shared.getBoolean(key, true)
        shared.edit().putBoolean(key, false).commit()
        app.appPreferencesRepository.get().apply {
            updateAppearance(Appearance.LIGHT)
            updateToolbarPosition(ToolbarPosition.TOP)
            updateOpenBlankNewTab(false)
            HomeSection.entries.forEach { updateHomeSection(it, visible = true) }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription(app.getString(R.string.nav_menu)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        originalSelection = app.store.get().state.selectedTabId
        app.historyRepository.get().apply {
            recordVisit(historyUrl, PageVisit(VisitType.LINK))
            recordObservation(historyUrl, PageObservation(title = "Native home history fixture"))
        }
        instrumentation.runOnMainSync {
            fixtures += app.tabsUseCases.get().addTab(historyUrl, title = "Native home continuation fixture", selectTab = true, startLoading = false)
            homeId = app.tabsUseCases.get().addTab(LEGACY_NEW_TAB_URL, selectTab = true, private = false)
            fixtures += homeId
        }
        compose.waitUntil(30_000) { app.store.get().state.tabs.find { it.id == homeId }?.let {
            it.content.url == NATIVE_HOME_URL && it.engineState.engineSession != null &&
                it.content.title == "Midori" && !it.content.loading
        } == true }
        compose.onNodeWithTag("native_home").assertIsDisplayed()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Native home continuation fixture").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After
    fun restore(): Unit = runBlocking {
        instrumentation.runOnMainSync {
            originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
            fixtures.forEach { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
        }
        app.historyRepository.get().deleteVisitsFor(historyUrl)
        app.historyRepository.get().deleteVisitsFor("https://example.org/native-shortcut")
        app.appPreferencesRepository.get().apply {
            flow.first().homeShortcutsList.filter { it.title == "Home shortcut fixture" }.forEach { deleteHomeShortcut(it.id) }
            updateAppearance(preferences.appearance)
            updateToolbarPosition(preferences.toolbarPosition)
            updateOpenBlankNewTab(preferences.openBlankNewTab)
            updateHomeSection(HomeSection.SHORTCUTS, !preferences.hideHomeShortcuts)
            updateHomeSection(HomeSection.CONTINUE, !preferences.hideHomeContinue)
            updateHomeSection(HomeSection.RECENT_VISITS, !preferences.hideHomeRecentVisits)
            updateHomeSection(HomeSection.STORIES, !preferences.hideHomeStories)
            updateHomeSection(HomeSection.PRIVACY, !preferences.hideHomePrivacy)
        }
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboardingEnabled).commit()
        scenario.close()
    }

    @Test
    fun restoredPlaceholderShowsNativeSectionsWithEitherToolbarPosition(): Unit = runBlocking {
        assertNoWebSurface()
        compose.onNodeWithText(app.getString(R.string.home_shortcuts)).assertIsDisplayed()
        compose.onNode(hasText("Google") and hasAnyAncestor(hasTestTag("home_shortcut_tile")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("Wikipedia") and hasAnyAncestor(hasTestTag("home_shortcut_tile")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText(app.getString(R.string.home_continue)).assertIsDisplayed()
        compose.onNodeWithText("Native home continuation fixture").assertIsDisplayed()
        compose.onNodeWithText("Native home history fixture").assertIsDisplayed()
        capture("native-home-toolbar-top.png")
        app.appPreferencesRepository.get().updateToolbarPosition(ToolbarPosition.BOTTOM)
        compose.waitForIdle()
        compose.onNodeWithTag("native_home").assertIsDisplayed()
        capture("native-home-toolbar-bottom.png")
        compose.onNodeWithTag("native_home").performScrollToIndex(5)
        compose.onNodeWithText(app.getString(R.string.home_stories)).assertIsDisplayed()
        capture("native-home-stories.png")
        assertNoWebSurface()
    }

    @Test
    fun shortcutCanBeAddedAndOpenedAndHomeSurvivesActivityRecreation() {
        compose.onNodeWithTag("home_add_shortcut").performClick()
        compose.onNodeWithTag("home_shortcut_title").performTextInput("Home shortcut fixture")
        compose.onNodeWithTag("home_shortcut_url").performTextInput("https://example.org/native-shortcut")
        compose.onNodeWithTag("home_shortcut_save").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_shortcut_save").fetchSemanticsNodes().isEmpty()
        }
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("native_home").assertIsDisplayed()
        compose.onAllNodesWithText(app.getString(R.string.home_show_all))[0].performClick()
        compose.onNode(hasText("Home shortcut fixture") and hasAnyAncestor(hasTestTag("home_shortcuts_sheet")))
            .performScrollTo().performClick()
        compose.waitUntil(20_000) { app.store.get().state.tabs.find { it.id == homeId }?.content?.url == "https://example.org/native-shortcut" }
        compose.onNodeWithTag("native_home").assertDoesNotExist()
    }

    @Test
    fun firstWebPageRetainsHomeInGeckoHistoryAndSavedSessions() {
        instrumentation.runOnMainSync {
            app.sessionUseCases.get().loadUrl("data:text/html,<title>Native home navigation fixture</title><h1>Ready</h1>", sessionId = homeId)
        }
        compose.waitUntil(30_000) { app.store.get().state.tabs.find { it.id == homeId }?.content?.title == "Native home navigation fixture" }
        compose.waitUntil(10_000) { app.store.get().state.tabs.find { it.id == homeId }?.content?.canGoBack == true }
        compose.waitForIdle()
        compose.onNodeWithTag("native_home").assertDoesNotExist()
        scenario.onActivity { assertTrue(it.onBackPressedDispatcher.hasEnabledCallbacks()) }
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(20_000) { app.store.get().state.tabs.find { it.id == homeId }?.content?.url == NATIVE_HOME_URL }
        compose.onNodeWithTag("native_home").assertIsDisplayed()
        assertNoWebSurface()
        assertTrue(app.sessionStorage.get().save(app.store.get().state))
        val restored = app.sessionStorage.get().restore()!!.tabs.first { it.state.id == homeId }
        assertEquals(NATIVE_HOME_URL, restored.state.url)
        assertNotNull(restored.engineSessionState)
    }

    @Test
    fun privateHomeDoesNotComposeNormalHistoryOrShortcuts() {
        compose.onNodeWithTag("native_home_private").performClick()
        compose.waitUntil(20_000) { app.store.get().state.selectedTabId != homeId }
        fixtures += app.store.get().state.selectedTabId!!
        compose.onNodeWithTag("native_home").assertDoesNotExist()
        compose.onNodeWithText("Native home history fixture").assertDoesNotExist()
        compose.onNodeWithText("Google").assertDoesNotExist()
        assertTrue(app.store.get().state.tabs.first { it.id == app.store.get().state.selectedTabId }.content.private)
        assertNoWebSurface()
    }

    @Test
    fun compactHomeKeepsActionsReachableAndSupportsDarkAppearance(): Unit = runBlocking {
        val lightContrast = assertBrandVisible(dark = false)
        val density = app.resources.displayMetrics.density
        val shortcutIcons = compose.onAllNodesWithTag("home_shortcut_icon", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(shortcutIcons.isNotEmpty())
        shortcutIcons.forEach { node ->
            assertEquals(48f * density, node.boundsInRoot.width, 1f)
        }
        val continuationHeight = compose.onNodeWithTag("native_home_continue").fetchSemanticsNode().boundsInRoot.height / density
        assertTrue("Continuation height: $continuationHeight dp", continuationHeight in 71f..100f)
        app.appPreferencesRepository.get().updateAppearance(Appearance.DARK)
        compose.waitForIdle()
        compose.onNodeWithTag("native_home").assertIsDisplayed()
        val darkContrast = assertBrandVisible(dark = true)
        File(app.cacheDir, "home-brand-result.json").writeText(
            """{"height_dp":28,"light_contrast":$lightContrast,"dark_contrast":$darkContrast}""")
        capture("native-home-dark.png")
        compose.onNodeWithTag("native_home").performScrollToIndex(5)
        compose.onNodeWithText(app.getString(R.string.home_stories)).assertIsDisplayed()
    }

    @Test
    fun blockedRequestsUpdateHomeAndSurviveNavigationWhilePrivateBlocksStayPrivate(): Unit = runBlocking {
        lateinit var homeViewModel: NativeHomeViewModel
        lateinit var browserViewModel: BrowserScreenViewModel
        scenario.onActivity {
            homeViewModel = ViewModelProvider(it)[NativeHomeViewModel::class.java]
            browserViewModel = ViewModelProvider(it)[BrowserScreenViewModel::class.java]
        }
        val blockedCount = homeViewModel.blockedCount
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val url = "http://127.0.0.1:${server.localPort}/counter-fixture"
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine().split(' ')[1]
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = if (path.startsWith("/counter-fixture")) """<!doctype html><title>Counter fixture</title>
                            <script>window.onload = () => {
                            let done = 0;
                            for (let i = 0; i < 3; i++) {
                              const image = new Image();
                              image.onload = image.onerror = () => { if (++done === 3) document.title = 'Counter fixture complete'; };
                              image.src = 'https://pagead2.googlesyndication.com/pagead/midori-counter-fixture?' + location.search + i;
                            }
                            };</script>""".toByteArray() else byteArrayOf()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 ${if (body.isEmpty()) "404 Not Found" else "200 OK"}\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                } catch (_: IOException) { }
            }
        }
        try {
            val before = blockedCount.value
            instrumentation.runOnMainSync {
                app.store.get().state.tabs.first { it.id == homeId }.engineState.engineSession!!
                    .updateTrackingProtection(EngineSession.TrackingProtectionPolicy.none())
                app.sessionUseCases.get().loadUrl(url, sessionId = homeId)
            }
            compose.waitUntil(30_000) { app.store.get().state.tabs.first { it.id == homeId }.content.title == "Counter fixture complete" }
            compose.waitUntil(10_000) { blockedCount.value >= before + 3 }
            assertEquals(before + 3, blockedCount.value)
            instrumentation.runOnMainSync { app.sessionUseCases.get().loadUrl(NATIVE_HOME_URL, sessionId = homeId) }
            compose.waitUntil(20_000) { compose.onAllNodesWithTag("native_home").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("native_home").assertIsDisplayed()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(app.getString(R.string.home_blocked, before + 3)).fetchSemanticsNodes().isNotEmpty()
            }
            capture("native-home-blocking-counter.png")
            scenario.recreate()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(app.getString(R.string.home_blocked, before + 3)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(app.getString(R.string.home_blocked, before + 3)).assertIsDisplayed()

            var privateId = ""
            instrumentation.runOnMainSync {
                privateId = app.tabsUseCases.get().addTab(NATIVE_HOME_URL, selectTab = true, private = true)
                fixtures += privateId
            }
            compose.waitUntil(30_000) { app.store.get().state.tabs.find { it.id == privateId }?.let {
                it.engineState.engineSession != null && it.content.title == "Midori" && !it.content.loading
            } == true }
            instrumentation.runOnMainSync {
                app.store.get().state.tabs.first { it.id == privateId }.engineState.engineSession!!
                    .updateTrackingProtection(EngineSession.TrackingProtectionPolicy.none())
                app.sessionUseCases.get().loadUrl("$url?private", sessionId = privateId)
            }
            compose.waitUntil(30_000) { app.store.get().state.tabs.first { it.id == privateId }.content.title == "Counter fixture complete" }
            withTimeout(10_000) { browserViewModel.nativeBlockerState.first { it.blockedCount >= 3 } }
            delay(200)
            assertEquals("Private blocks must not enter persisted statistics", before + 3, blockedCount.value)
            File(app.cacheDir, "blocking-counter-result.json").writeText(
                """{"before":$before,"after":${blockedCount.value},"native_requests_blocked":3,"private_excluded":true}""")
        } finally {
            server.close()
            executor.shutdownNow()
            app.historyRepository.get().deleteVisitsFor(url)
        }
    }

    @Test
    fun amazonShortcutLoadsARealFaviconOnTheConnectedDevice(): Unit = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("homeLiveFavicons") == "true")
        val amazon = preferences.homeShortcutsList.firstOrNull { it.url.contains("amazon.com") }
        val url = amazon?.url ?: "https://amazon.com"
        if (amazon == null) app.appPreferencesRepository.get().saveHomeShortcut(
            "native-home-amazon-fixture", "Amazon favicon fixture", url)
        try {
            compose.waitUntil(60_000) {
                compose.onAllNodesWithTag("home_favicon:${homeIconOrigin(url)}", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            lateinit var viewModel: NativeHomeViewModel
            scenario.onActivity { viewModel = ViewModelProvider(it)[NativeHomeViewModel::class.java] }
            val favicon = withTimeout(60_000) { viewModel.favicon(url).first { it != null } }!!
            assertNotEquals(mozilla.components.browser.icons.Icon.Source.GENERATOR, favicon.source)
            assertTrue(favicon.bitmap.width >= 48)
            File(app.cacheDir, "amazon-favicon-result.json").writeText(
                """{"width":${favicon.bitmap.width},"height":${favicon.bitmap.height},"source":"${favicon.source}"}""")
            capture("native-home-amazon.png")
            compose.onAllNodesWithText(app.getString(R.string.home_show_all))[0].performClick()
            compose.onNode(hasText(amazon?.title ?: "Amazon favicon fixture") and hasAnyAncestor(hasTestTag("home_shortcuts_sheet")))
                .assertIsDisplayed()
            capture("native-home-shortcuts.png")
        } finally {
            if (amazon == null) app.appPreferencesRepository.get().deleteHomeShortcut("native-home-amazon-fixture")
        }
    }

    private fun assertNoWebSurface() {
        scenario.onActivity { activity ->
            fun check(view: View) {
                assertFalse(view.javaClass.name, view is android.webkit.WebView || view.javaClass.name.contains("GeckoView"))
                if (view is ViewGroup) for (index in 0 until view.childCount) check(view.getChildAt(index))
            }
            check(activity.window.decorView)
        }
    }

    private fun assertBrandVisible(dark: Boolean): Double {
        val brand = compose.onNodeWithTag("native_home_brand")
            .assertIsDisplayed().assertContentDescriptionEquals("Midori")
        val bounds = brand.fetchSemanticsNode().boundsInRoot
        assertEquals(28f * app.resources.displayMetrics.density, bounds.height, 1f)
        assertEquals(392f / 79f, bounds.width / bounds.height, 0.02f)
        compose.onNodeWithTag("native_home_private").assertIsDisplayed()
        var contrast = 0.0
        compose.waitUntil(10_000) {
            val bitmap = brand.captureToImage().asAndroidBitmap()
            try {
                val background = bitmap.getPixel(bitmap.width - 1, 0)
                if ((ColorUtils.calculateLuminance(background) < 0.5) != dark) return@waitUntil false
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                val green = pixels.firstOrNull { color ->
                    android.graphics.Color.red(color) in 2..6 &&
                        android.graphics.Color.green(color) in 160..164 &&
                        android.graphics.Color.blue(color) in 101..105
                } ?: return@waitUntil false
                contrast = ColorUtils.calculateContrast(green, background)
                contrast >= 3.0
            } finally {
                bitmap.recycle()
            }
        }
        return contrast
    }

    private fun capture(name: String) {
        val screenshot = if (name == "native-home-shortcuts.png") {
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(app.cacheDir, name).outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
}

