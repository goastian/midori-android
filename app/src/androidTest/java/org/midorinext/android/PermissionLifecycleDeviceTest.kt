package org.midorinext.android

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.diagnostics.ScenarioMetrics
import org.midorinext.android.mozac.permissions.PermissionsFeature
import org.mozilla.geckoview.GeckoSession

class PermissionLifecycleDeviceTest {
    @Test fun nativePermissionsKeepTheirOriginWhenSelectionChangesDuringDiskAccess() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        instrumentation.runOnMainSync { app.initializeBrowser("permission_lifecycle_test", restoreSession = false) }
        val storageScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val disk = OnDiskSitePermissionsStorage(app, storageScope)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val privateLookups = CopyOnWriteArrayList<Boolean>()
        val storage = object : SitePermissionsStorage by disk {
            override suspend fun findSitePermissionsBy(origin: String, includeTemporary: Boolean, private: Boolean): SitePermissions? {
                privateLookups += private
                entered.complete(Unit)
                release.await()
                return disk.findSitePermissionsBy(origin, includeTemporary, private)
            }
        }
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val audio = ByteBuffer.allocate(8044).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(8036); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(8000)
        }.array()
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 2000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine()?.split(' ')?.getOrNull(1) ?: return@use
                        while (!input.readLine().isNullOrEmpty()) { }
                        val bytes = if (path == "/tone.wav") audio else
                            "<title>Permission lifecycle fixture</title><audio src='/tone.wav' preload='auto'></audio>".toByteArray()
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
        val origin = createTab("http://127.0.0.1:${server.localPort}/", private = false)
        val other = createTab("about:blank", private = true)
        val store = BrowserStore(
            BrowserState(tabs = listOf(origin, other), selectedTabId = origin.id),
            middleware = EngineMiddleware.create(app.engine.get(), trimMemoryAutomatically = false),
        )
        val prompts = AtomicInteger()
        val appPermissions = AtomicInteger()
        val feature = PermissionsFeature(app, store, storage,
            onPrompt = { request, _ -> prompts.incrementAndGet(); request.reject() },
            onNeedToRequestPermissions = { appPermissions.incrementAndGet() },
        )
        val metricsEnabled = ScenarioMetrics.enabled
        ScenarioMetrics.start()
        try {
            instrumentation.runOnMainSync {
                feature.start()
                store.dispatch(EngineAction.CreateEngineSessionAction(origin.id))
            }
            await("native engine session") { store.state.tabs.first().engineState.engineSession != null }
            val session = store.state.tabs.first().engineState.engineSession!!
            val gecko = session.javaClass.getDeclaredField("geckoSession").apply { isAccessible = true }.get(session) as GeckoSession
            instrumentation.runOnMainSync { gecko.setActive(true) }
            await("real permission lookup") { entered.isCompleted }
            val nativePermissions = store.state.tabs.first().content.permissionRequestsList.flatMap { it.permissions }.map { it.name }
            assertTrue(nativePermissions.isNotEmpty())
            store.dispatch(TabListAction.SelectTabAction(other.id))
            await("selection changed during storage access") { store.state.selectedTabId == other.id }
            release.complete(Unit)
            await("origin permissions consumed") {
                store.state.tabs.first().content.permissionRequestsList.isEmpty() && privateLookups.isNotEmpty()
            }
            var resolved = 0L
            instrumentation.runOnMainSync { resolved = ScenarioMetrics.snapshot().getLong("background_autoplay_resolved") }
            assertTrue(resolved > 0)
            assertTrue(privateLookups.all { !it })
            assertTrue(store.state.tabs.last().content.permissionRequestsList.isEmpty())
            assertEquals(0, prompts.get())
            assertEquals(0, appPermissions.get())
            File(app.cacheDir, "permission-lifecycle-results.json").writeText(JSONObject()
                .put("native_permissions", org.json.JSONArray(nativePermissions))
                .put("background_autoplay_resolved", resolved)
                .put("lookups_used_origin_privacy", privateLookups.all { !it })
                .put("origin_requests_consumed_after_selection_changed", true).toString(2))
        } finally {
            release.complete(Unit)
            instrumentation.runOnMainSync {
                feature.stop()
                store.dispatch(TabListAction.RemoveAllTabsAction(recoverable = false))
            }
            ScenarioMetrics.enabled = metricsEnabled
            storageScope.cancel()
            server.close()
            executor.shutdownNow()
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $description" }
            SystemClock.sleep(100)
        }
    }
}
