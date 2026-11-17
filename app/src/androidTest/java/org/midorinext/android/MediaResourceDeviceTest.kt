package org.midorinext.android

import android.content.Intent
import android.os.Debug
import android.os.SystemClock
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.MediaSessionAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.concept.engine.mediasession.MediaSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule

class MediaResourceDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as MidoriApplication
    private val store get() = app.store.get()

    @Test
    fun pausedAndStoppedMediaReleaseSessionsWhilePlayingAndPipStayProtected(): Unit = runBlocking {
        val shared = PreferenceManager.getDefaultSharedPreferences(app)
        val key = app.getString(R.string.pref_key_show_onboarding)
        val onboarding = shared.getBoolean(key, true)
        val preferences = app.appPreferencesRepository.get().flow.first()
        shared.edit().putBoolean(key, false).commit()
        app.appPreferencesRepository.get().updateCloseTabsOnExit(false)
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val audio = tone()
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val reader = socket.getInputStream().bufferedReader()
                        val path = reader.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val bytes = if (path == "/tone.wav") audio else page(path).toByteArray()
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 200 OK\r\nContent-Type: ${if (path == "/tone.wav") "audio/wav" else "text/html"}\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray())
                            write(bytes)
                            flush()
                        }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        })
        compose.waitForIdle()
        await("restoration") { store.state.restoreComplete }
        val originalIds = store.state.tabs.map { it.id }
        val originalSelection = store.state.selectedTabId
        val fixtures = mutableListOf<String>()
        val reports = org.json.JSONArray()
        var privateTabs = false
        var report = JSONObject()
        fun tab(id: String) = store.state.tabs.first { it.id == id }
        fun add(name: String, media: Boolean, private: Boolean = privateTabs): String {
            var id = ""
            instrumentation.runOnMainSync {
                id = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/$name", private = private, selectTab = true)
            }
            fixtures += id
            compose.waitForIdle()
            assertEquals("The fixture must remain selected", id, store.state.selectedTabId)
            await("$name document") {
                !tab(id).content.loading && tab(id).content.title == "Fixture /$name" &&
                    (!media || tab(id).content.firstContentfulPaint)
            }
            if (media) {
                await("$name window") {
                    instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == app.packageName
                }
                val bounds = android.graphics.Rect()
                await("$name renderer") {
                    var visible = false
                    scenario.onActivity { visible = findRenderer(it.window.decorView)?.getGlobalVisibleRect(bounds) == true }
                    visible
                }
                instrumentation.uiAutomation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}").use {
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                }
                await("$name playback") { tab(id).mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING }
            }
            return id
        }
        try {
            for (mode in listOf(false, true)) {
                privateTabs = mode
                report = JSONObject().put("private", mode)
                reports.put(report)
                try {
                    val stopped = add("media-stopped", true)
                    instrumentation.runOnMainSync { tab(stopped).mediaSessionState!!.controller.pause() }
                    await("stop fixture paused") { tab(stopped).mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
                    store.dispatch(MediaSessionAction.UpdateMediaPlaybackStateAction(stopped, MediaSession.PlaybackState.STOPPED))
                    await("stopped media") { tab(stopped).mediaSessionState?.playbackState == MediaSession.PlaybackState.STOPPED }
                    val pausedEngines = mutableListOf<mozilla.components.concept.engine.EngineSession>()
                    val pausedTabs = List(app.memoryBudget.profile.foreground + 1) { index ->
                        add("media-paused-$index", true).also { id ->
                            pausedEngines += tab(id).engineState.engineSession!!
                            instrumentation.runOnMainSync { tab(id).mediaSessionState!!.controller.pause() }
                            await("paused media $index") { tab(id).mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
                        }
                    }
                    val paused = pausedTabs.first()
                    val pausedSession = pausedEngines.first()
                    val pip = add("media-pip", true)
                    instrumentation.runOnMainSync { tab(pip).mediaSessionState!!.controller.pause() }
                    await("paused PiP fixture") { tab(pip).mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
                    store.dispatch(ContentAction.PictureInPictureChangedAction(pip, true))
                    await("PiP state") { tab(pip).content.pictureInPictureEnabled }
                    val playing = add("media-playing", true)
                    report.put("resident_before_idle_pressure", store.state.tabs.count { it.engineState.engineSession != null })
                    repeat(2) { add("idle-$it", false, private = !mode) }
                    instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
                    }
                    await("actual background budget") { app.memoryBudget.policy.limit == app.memoryBudget.profile.background }
                    report.put("actual_background_budget", app.memoryBudget.policy.limit)
                    SystemClock.sleep(2_000)
                    await("paused and stopped sessions released", 30_000) {
                        pausedTabs.all { tab(it).engineState.engineSession == null } && tab(stopped).engineState.engineSession == null
                    }
                    await("resident budget with selected, playing and PiP protected") {
                        store.state.tabs.count { it.engineState.engineSession != null } <= maxOf(3, app.memoryBudget.policy.limit)
                    }
                    assertNotNull(tab(paused).engineState.engineSessionState)
                    assertNotNull(tab(stopped).engineState.engineSessionState)
                    pausedSession.let {
                        val gecko = it.javaClass.getDeclaredField("geckoSession").apply { isAccessible = true }.get(it) as org.mozilla.geckoview.GeckoSession
                        instrumentation.runOnMainSync { assertFalse(gecko.isOpen) }
                    }
                    assertNotNull(tab(playing).engineState.engineSession)
                    assertEquals(MediaSession.PlaybackState.PLAYING, tab(playing).mediaSessionState?.playbackState)
                    assertNotNull(tab(pip).engineState.engineSession)
                    assertEquals(originalIds.size + fixtures.size, store.state.tabs.size)
                    val memory = Debug.MemoryInfo().also(Debug::getMemoryInfo)
                    report.put("logical_tabs", store.state.tabs.size)
                        .put("resident_limit", app.memoryBudget.policy.limit)
                        .put("resident_sessions", store.state.tabs.count { it.engineState.engineSession != null })
                        .put("main_process_pss_kb", memory.totalPss)
                        .put("paused_and_stopped_recoverable", true)
                        .put("paused_fixtures", pausedTabs.size)
                        .put("playing_preserved", true).put("pip_state_protected", true)
                    assertEquals(MediaSession.PlaybackState.PLAYING, tab(playing).mediaSessionState?.playbackState)
                    instrumentation.runOnMainSync { tab(playing).mediaSessionState!!.controller.pause() }
                    await("background playback paused") { tab(playing).mediaSessionState?.playbackState == MediaSession.PlaybackState.PAUSED }
                    await("background paused session released") { tab(playing).engineState.engineSession == null }
                    report.put("background_playing_preserved_and_paused_released", true)
                    returnToBrowser()
                    store.dispatch(ContentAction.PictureInPictureChangedAction(pip, false))
                    repeat(app.memoryBudget.profile.foreground) { add("idle-after-pip-$it", false) }
                    await("PiP protection released") { tab(pip).engineState.engineSession == null }
                    store.dispatch(TabListAction.SelectTabAction(paused))
                    await("paused page restored") { tab(paused).engineState.engineSession != null && !tab(paused).content.loading && tab(paused).content.title == "Fixture /media-paused-0" }
                    assertNotSame(pausedSession, tab(paused).engineState.engineSession)
                    assertNotEquals(MediaSession.PlaybackState.PLAYING, tab(paused).mediaSessionState?.playbackState)
                    report.put("restored_same_tab_without_autoplay", true)
                    assertTrue(app.sessionStorage.get().save(store.state))
                    val saved = JSONObject(File(app.filesDir, "mozilla_components_session_storage_gecko.json").readText())
                        .getJSONArray("sessionStateTuples")
                    val savedIds = (0 until saved.length()).map { saved.getJSONObject(it).getJSONObject("session").getString("uuid") }.toSet()
                    store.state.tabs.filter { it.id in fixtures }.forEach { fixture ->
                        assertEquals(!fixture.content.private, fixture.id in savedIds)
                    }
                    report.put("normal_tabs_persisted_private_tabs_excluded", true)
                } catch (failure: Throwable) {
                    report.put("failure", failure.toString())
                    throw failure
                } finally {
                    report.put("fixtures_before_cleanup", org.json.JSONArray(store.state.tabs.filter { it.id in fixtures }.map {
                        JSONObject().put("title", it.content.title).put("private", it.content.private)
                            .put("resident", it.engineState.engineSession != null)
                            .put("selected", it.id == store.state.selectedTabId).put("first_paint", it.content.firstContentfulPaint)
                            .put("loading", it.content.loading).put("form", it.content.hasFormData)
                            .put("pip", it.content.pictureInPictureEnabled).put("playback", it.mediaSessionState?.playbackState?.name)
                    }))
                    File(app.cacheDir, "media-resource-results.json").writeText(reports.toString(2))
                    runCatching { returnToBrowser() }.onFailure { report.put("cleanup_return_failure", it.toString()) }
                    originalSelection?.let { store.dispatch(TabListAction.SelectTabAction(it)) }
                    await("original selection") { store.state.selectedTabId == originalSelection }
                    store.dispatch(TabListAction.RemoveTabsAction(fixtures))
                    await("fixture cleanup") { store.state.tabs.none { it.id in fixtures } }
                    assertEquals(originalIds, store.state.tabs.map { it.id })
                    assertTrue(app.sessionStorage.get().save(store.state))
                    fixtures.clear()
                    File(app.cacheDir, "media-resource-results.json").writeText(reports.toString(2))
                }
            }
        } finally {
            server.close()
            executor.shutdownNow()
            try {
                scenario.close()
            } finally {
                app.appPreferencesRepository.get().updateCloseTabsOnExit(preferences.closeTabsOnExit)
                shared.edit().putBoolean(key, onboarding).commit()
            }
        }
    }

    private fun returnToBrowser() {
        instrumentation.uiAutomation.executeShellCommand(
            "am start -n ${app.packageName}/${MainActivity::class.java.name} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
        ).use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        await("browser foreground") {
            var resumed = false
            instrumentation.runOnMainSync {
                resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .any { it is MainActivity && it.hasWindowFocus() }
            }
            resumed
        }
        compose.waitForIdle()
    }

    private fun findRenderer(view: android.view.View): org.mozilla.geckoview.GeckoView? = when (view) {
        is org.mozilla.geckoview.GeckoView -> view
        is android.view.ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findRenderer(view.getChildAt(it)) }
        else -> null
    }

    @Test
    fun updatedWorkManagerCompletesTheRealSyncWorkerWithoutAnAccountMatch() {
        val manager = androidx.work.WorkManager.getInstance(app)
        val request = androidx.work.OneTimeWorkRequestBuilder<org.midorinext.android.sync.SyncWorker>()
            .setInputData(androidx.work.workDataOf("account" to "dependency-validation-no-account"))
            .build()
        try {
            manager.enqueue(request).result.get(10, java.util.concurrent.TimeUnit.SECONDS)
            await("SyncWorker completion", 30_000) { manager.getWorkInfoById(request.id).get()?.state?.isFinished == true }
            assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(request.id).get()?.state)
        } finally {
            manager.cancelWorkById(request.id).result.get(10, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private fun await(description: String, timeout: Long = 20_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $description; selected playback=${store.state.tabs.firstOrNull { it.id == store.state.selectedTabId }?.mediaSessionState?.playbackState}" }
            SystemClock.sleep(100)
        }
    }

    private fun page(path: String) = """<meta name="viewport" content="width=device-width,initial-scale=1"><title>Fixture $path</title>
        <audio id="a" loop src="/tone.wav"></audio><button style="position:fixed;inset:0;font-size:32px" onclick="a.play()">$path</button>
        <script>navigator.mediaSession.metadata=new MediaMetadata({title:'$path'});
        navigator.mediaSession.setActionHandler('stop',()=>{a.pause();a.removeAttribute('src');a.load();});</script>"""

    private fun tone(): ByteArray {
        val rate = 8_000
        val size = rate * 4 * 2
        return ByteBuffer.allocate(44 + size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(rate); putInt(rate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(size)
            repeat(size / 2) { putShort((64 * kotlin.math.sin(2 * Math.PI * 440 * it / rate)).toInt().toShort()) }
        }.array()
    }
}
