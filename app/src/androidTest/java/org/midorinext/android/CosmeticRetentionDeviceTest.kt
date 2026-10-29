package org.midorinext.android

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import mozilla.components.browser.state.action.TabListAction
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CosmeticRetentionDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun spaRetentionIsBoundedAndDisablingRestoresNormalAndPrivateDocuments() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val minutes = InstrumentationRegistry.getArguments().getString("soakMinutes", "0").toInt()
        val source = app.assets.open("adblock/content/cosmetic-procedural.js").bufferedReader().use { it.readText() }
        val template = instrumentation.context.assets.open("cosmetic-retention.html").bufferedReader().use { it.readText() }
        val results = org.json.JSONArray()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val executor = Executors.newSingleThreadExecutor()
        val store = app.store.get()
        val visibility = app.memoryBudget.policy.javaClass.getDeclaredField("visible").apply { isAccessible = true }
        val originalSelected = store.state.selectedTabId
        val tabs = mutableListOf<String>()
        executor.submit {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val request = reader.readLine()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val cycles = if (request.contains("soak")) minutes.coerceAtLeast(1) else 5
                        val interval = if (request.contains("soak")) 60_000 else 0
                        val html = template.replace("/*SOURCE*/", source) + "<script>runRetention($cycles,$interval).then(value=>document.title=JSON.stringify(value));</script>"
                        val bytes = html.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(bytes); flush()
                        }
                    }
                } catch (_: java.io.IOException) { }
            }
        }
        try {
            instrumentation.runOnMainSync { activity.activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            for (private in listOf(false, true)) {
                var id = ""
                instrumentation.runOnMainSync {
                    id = app.tabsUseCases.get().addTab("http://127.0.0.1:${server.localPort}/${if (minutes > 0 && !private) "soak" else "quick"}", private = private, selectTab = true)
                }
                tabs += id
                val deadline = SystemClock.elapsedRealtime() + if (minutes > 0 && !private) (minutes + 10) * 60_000L else 180_000L
                var result: JSONObject? = null
                var progress = ""
                var checkedVisibilityAt = 0L
                while (result == null) {
                    if (SystemClock.elapsedRealtime() - checkedVisibilityAt >= 1000) {
                        var visible = false
                        instrumentation.runOnMainSync { visible = visibility.getBoolean(app.memoryBudget.policy) }
                        check(visible) { "Device left Midori during retention measurement" }
                        checkedVisibilityAt = SystemClock.elapsedRealtime()
                    }
                    val title = store.state.tabs.find { it.id == id }?.content?.title.orEmpty()
                    if (title.startsWith("{\"ok\"")) result = JSONObject(title)
                    if (title.startsWith("{\"progress\"" ) && title != progress) {
                        progress = title
                        File(app.cacheDir, "cosmetic-retention-progress.json").writeText(title)
                        instrumentation.sendStatus(0, Bundle().apply { putString("retentionProgress", title) })
                    }
                    check(SystemClock.elapsedRealtime() < deadline) { "Retention timed out: $title" }
                    SystemClock.sleep(250)
                }
                results.put(result.put("private", private))
                assertTrue(result.toString(), result.getBoolean("ok"))
            }
            File(app.cacheDir, "cosmetic-retention-results.json").writeText(results.toString(2))
            instrumentation.sendStatus(0, Bundle().apply { putString("retention", results.toString()) })
        } finally {
            try {
                instrumentation.runOnMainSync { activity.activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                store.dispatch(TabListAction.RemoveTabsAction(tabs))
                originalSelected?.let { store.dispatch(TabListAction.SelectTabAction(it)) }
                val cleanupDeadline = SystemClock.elapsedRealtime() + 30_000
                while (store.state.tabs.any { it.id in tabs } || store.state.selectedTabId != originalSelected) {
                    check(SystemClock.elapsedRealtime() < cleanupDeadline) { "Retention fixture cleanup did not finish" }
                    SystemClock.sleep(100)
                }
            } finally {
                server.close()
                executor.shutdownNow()
            }
        }
    }
}
