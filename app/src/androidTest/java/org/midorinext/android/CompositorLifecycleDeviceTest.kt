package org.midorinext.android

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.ToolbarPosition

@RunWith(AndroidJUnit4::class)
class CompositorLifecycleDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as MidoriApplication
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity
    private lateinit var preferences: AppPreferences
    private val samples = JSONArray()
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val executor = Executors.newSingleThreadExecutor()
    private var fixtureId: String? = null
    private var originalSelection: String? = null
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var onboardingEnabled = false
    private var resultName = "compositor-lifecycle"

    @Before
    fun prepare(): Unit = runBlocking {
        assertTrue(BuildConfig.DEBUG)
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val reader = socket.getInputStream().bufferedReader()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val body = """
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>body{margin:0;background:rgb(24,160,96);height:3000px}</style>
                            <p>Compositor lifecycle fixture</p>
                            <script>let frame=0;setInterval(()=>document.title='Compositor frame '+(++frame),100)</script>
                        """.trimIndent().toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                        }
                    }
                } catch (e: java.io.IOException) {
                    if (!server.isClosed) throw e
                }
            }
        }
        preferences = app.appPreferencesRepository.get().flow.first()
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        onboardingEnabled = shared.getBoolean(key, true)
        shared.edit().putBoolean(key, false).commit()
        app.appPreferencesRepository.get().updateHideToolbarOnScroll(true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity {
            activity = it
            originalOrientation = it.requestedOrientation
        }
        compose.waitUntil(30_000) { app.store.get().state.restoreComplete }
        originalSelection = app.store.get().state.selectedTabId
        instrumentation.runOnMainSync {
            fixtureId = app.tabsUseCases.get().addTab(
                "http://127.0.0.1:${server.localPort}/compositor", private = true, selectTab = true,
            )
        }
        compose.waitUntil(20_000) { pageFrame() > 0 }
        assertPagePainted()
    }

    @After
    fun restore(): Unit = runBlocking {
        File(app.cacheDir, "$resultName.json").writeText(samples.toString(2))
        try {
            if (::scenario.isInitialized) {
                scenario.moveToState(Lifecycle.State.RESUMED)
                scenario.onActivity { it.requestedOrientation = originalOrientation }
                instrumentation.runOnMainSync {
                    originalSelection?.let { app.store.get().dispatch(TabListAction.SelectTabAction(it)) }
                    fixtureId?.let { app.store.get().dispatch(TabListAction.RemoveTabAction(it)) }
                }
            }
            if (::preferences.isInitialized) {
                app.appPreferencesRepository.get().updateToolbarPosition(preferences.toolbarPosition)
                app.appPreferencesRepository.get().updateHideToolbarOnScroll(preferences.hideToolbarOnScroll)
            }
            PreferenceManager.getDefaultSharedPreferences(app).edit()
                .putBoolean(app.getString(R.string.pref_key_show_onboarding), onboardingEnabled).commit()
        } finally {
            if (::scenario.isInitialized) scenario.close()
            server.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun addressKeyboardAndFindInPageKeepRenderingAtEitherToolbarPosition(): Unit = runBlocking {
        resultName = "compositor-keyboard"
        for (position in listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM)) {
            app.appPreferencesRepository.get().updateToolbarPosition(position)
            compose.waitForIdle()
            repeat(3) { cycle ->
                recordStep("address-$position-$cycle") {
                    compose.onNodeWithText("127.0.0.1").performClick()
                    awaitIme(true)
                    instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    awaitIme(false)
                    compose.waitForIdle()
                    if (compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty()) {
                        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                        compose.waitForIdle()
                    }
                }
                recordStep("find-$position-$cycle") {
                    compose.onNodeWithContentDescription(app.getString(R.string.nav_menu)).performClick()
                    compose.onNodeWithText(app.getString(R.string.menu_more_options)).performScrollTo().performClick()
                    compose.onNodeWithText(app.getString(R.string.menu_find_in_page)).performScrollTo().performClick()
                    compose.onNode(hasSetTextAction()).assertIsFocused()
                    awaitIme(true)
                    compose.onNodeWithContentDescription("close").performClick()
                    if (imeVisible()) instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    awaitIme(false)
                    compose.waitForIdle()
                }
            }
        }
    }

    @Test
    fun backgroundAndRotationResumeTheCompositor(): Unit = runBlocking {
        resultName = "compositor-window-lifecycle"
        for (position in listOf(ToolbarPosition.TOP, ToolbarPosition.BOTTOM)) {
            app.appPreferencesRepository.get().updateToolbarPosition(position)
            compose.waitForIdle()
            repeat(3) { cycle ->
                recordStep("background-$position-$cycle") {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    SystemClock.sleep(300)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.waitForIdle()
                }
                for ((requested, expected) in listOf(
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to Configuration.ORIENTATION_LANDSCAPE,
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to Configuration.ORIENTATION_PORTRAIT,
                )) {
                    recordStep("rotation-$position-$cycle-$expected") {
                        scenario.onActivity { it.requestedOrientation = requested }
                        compose.waitUntil(10_000) { app.resources.configuration.orientation == expected }
                        compose.waitForIdle()
                    }
                }
            }
        }
    }

    private fun recordStep(label: String, action: () -> Unit) {
        val surface = onMain { findSurface(activity.window.decorView)!! }
        val before = onMain { surface.width to surface.height }
        val changes = AtomicInteger()
        val creates = AtomicInteger()
        val destroys = AtomicInteger()
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { creates.incrementAndGet() }
            override fun surfaceDestroyed(holder: SurfaceHolder) { destroys.incrementAndGet() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { changes.incrementAndGet() }
        }
        onMain { surface.holder.addCallback(callback) }
        val started = SystemClock.elapsedRealtime()
        var passed = false
        try {
            action()
            val frame = pageFrame()
            compose.waitUntil(10_000) { pageFrame() > frame }
            assertPagePainted()
            assertEquals(fixtureId, app.store.get().state.selectedTabId)
            passed = true
        } catch (failure: Throwable) {
            runCatching {
                capture(label)
                File(app.cacheDir, "compositor-failure-semantics.txt")
                    .writeText(compose.onAllNodes(isRoot(), useUnmergedTree = true).printToString())
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        } finally {
            onMain { surface.holder.removeCallback(callback) }
            samples.put(JSONObject().put("step", label).put("passed", passed)
                .put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                .put("surface_changes", changes.get()).put("surface_creates", creates.get())
                .put("surface_destroys", destroys.get()).put("width_before", before.first)
                .put("height_before", before.second))
        }
    }

    private fun capture(label: String) {
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(app.cacheDir, "compositor-$label.png").outputStream().use {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun assertPagePainted() {
        val (x, y) = onMain {
            val surface = findSurface(activity.window.decorView)!!
            assertTrue(surface.holder.surface.isValid)
            val location = IntArray(2)
            surface.getLocationOnScreen(location)
            (location[0] + surface.width / 2) to (location[1] + surface.height / 3)
        }
        compose.waitUntil(10_000) {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                val pixel = screenshot.getPixel(x, y)
                kotlin.math.abs(Color.red(pixel) - 24) <= 12 &&
                    kotlin.math.abs(Color.green(pixel) - 160) <= 12 && kotlin.math.abs(Color.blue(pixel) - 96) <= 12
            } finally {
                screenshot.recycle()
            }
        }
    }

    private fun awaitIme(visible: Boolean) = compose.waitUntil(10_000) { imeVisible() == visible }

    private fun imeVisible(): Boolean = onMain {
        ViewCompat.getRootWindowInsets(activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    }

    private fun pageFrame(): Int = app.store.get().state.tabs.find { it.id == fixtureId }
        ?.content?.title?.removePrefix("Compositor frame ")?.toIntOrNull() ?: 0

    private fun findSurface(view: View): SurfaceView? = when (view) {
        is SurfaceView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findSurface(view.getChildAt(it)) }
        else -> null
    }

    private fun <T> onMain(block: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
