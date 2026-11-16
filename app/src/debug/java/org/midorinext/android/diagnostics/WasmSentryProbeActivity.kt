package org.midorinext.android.diagnostics

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.util.Base64
import io.sentry.Sentry
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.midorinext.android.MidoriApplication
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class WasmSentryProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: GeckoSession? = null
    private var mode = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra("mode").orEmpty()
        if (mode !in setOf("early", "late")) {
            finish()
            return
        }
        File(filesDir, "wasm-sentry-probe.json").delete()
        scope.launch {
            val diagnostics = (application as MidoriApplication).diagnostics.get()
            diagnostics.setEnabled(false).join()
            if (mode == "early") diagnostics.setEnabled(true).join()
            val config = File(cacheDir, "wasm-sentry-probe.yaml").apply {
                writeText("""
                    env:
                      MOZ_DISABLE_NONLOCAL_CONNECTIONS: "1"
                      MOZ_FORCE_DISABLE_E10S: "1"
                      MOZ_FORCE_DISABLE_FISSION: "1"
                    prefs:
                      browser.tabs.remote.autostart: false
                """.trimIndent())
            }
            val runtime = GeckoRuntime.create(this@WasmSentryProbeActivity,
                GeckoRuntimeSettings.Builder().configFilePath(config.absolutePath).build())
            val view = GeckoView(this@WasmSentryProbeActivity)
            setContentView(view)
            val probeSession = GeckoSession()
            session = probeSession
            probeSession.contentDelegate = object : GeckoSession.ContentDelegate {
                override fun onTitleChange(session: GeckoSession, title: String?) {
                    when (title) {
                        "wasm_ready" -> scope.launch {
                            if (mode == "late") diagnostics.setEnabled(true).join()
                            delay(1_000)
                            loadWasm(trap = true)
                        }
                        "wasm_trap_caught", "wasm_unexpected", "wasm_setup_error" -> {
                            val sentryEnabled = Sentry.isEnabled()
                            val eventId = Sentry.captureException(IllegalStateException("WebAssembly integration probe"))
                            File(filesDir, "wasm-sentry-probe.json").writeText(JSONObject(mapOf(
                                "mode" to mode, "result" to title, "pid" to Process.myPid(),
                                "sentry_enabled" to sentryEnabled, "control_event_id" to eventId.toString(),
                            )).toString())
                            finish()
                        }
                    }
                }
            }
            probeSession.open(runtime)
            view.setSession(probeSession)
            probeSession.setActive(true)
            loadWasm(trap = false)
        }
    }

    private fun loadWasm(trap: Boolean) {
        val body = if (trap) "10,5,1,3,0,0,11" else "10,4,1,2,0,11"
        val script = """
            try {
                const module = new WebAssembly.Module(new Uint8Array([
                    0,97,115,109,1,0,0,0,1,4,1,96,0,0,3,2,1,0,7,5,1,1,102,0,0,$body
                ]));
                new WebAssembly.Instance(module).exports.f();
                document.title = "${if (trap) "wasm_unexpected" else "wasm_ready"}";
            } catch (error) {
                document.title = ${if (trap) "error instanceof WebAssembly.RuntimeError ? 'wasm_trap_caught' : 'wasm_setup_error'" else "'wasm_setup_error'"};
            }
        """.trimIndent()
        val html = "<!doctype html><meta charset=utf-8><script>$script</script>"
        val encoded = Base64.encodeToString(html.toByteArray(), Base64.NO_WRAP)
        session?.loadUri("data:text/html;base64,$encoded")
    }

    override fun onDestroy() {
        session?.close()
        scope.cancel()
        super.onDestroy()
    }
}
