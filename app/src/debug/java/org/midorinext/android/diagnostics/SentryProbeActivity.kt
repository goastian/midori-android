package org.midorinext.android.diagnostics

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.midorinext.android.MidoriApplication
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryItemType
import io.sentry.hints.Cached
import io.sentry.util.HintUtils
import java.io.File
import java.io.StringReader
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class SentryProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var geckoSession: GeckoSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = intent.getStringExtra("probe")
        if (probe !in setOf("crash", "native-crash", "handled-error", "sigkill", "disable", "replay-native", "enable", "status", "gecko-crash")) {
            finish()
            return
        }
        scope.launch {
            if (probe == "status") {
                val options = Sentry.getCurrentScopes().options
                val androidOptions = options as? io.sentry.android.core.SentryAndroidOptions
                File(filesDir, "diagnostics-probe-status.json").writeText(JSONObject(mapOf(
                    "sentry_enabled" to Sentry.isEnabled(),
                    "anr_enabled" to (androidOptions?.isAnrEnabled ?: false),
                    "ndk_enabled" to (androidOptions?.isEnableNdk ?: false),
                    "sentry_uncaught_handler" to options.isEnableUncaughtExceptionHandler,
                    "uncaught_handler" to Thread.getDefaultUncaughtExceptionHandler()?.javaClass?.name,
                    "integrations" to options.integrations.map { it.javaClass.simpleName },
                    "processors" to options.eventProcessors.map { it.javaClass.simpleName },
                )).toString())
                finish()
                return@launch
            }
            (application as MidoriApplication).diagnostics.get().setEnabled(probe != "disable").join()
            if (probe == "disable" || probe == "enable") {
                finish()
                return@launch
            }
            if (probe == "gecko-crash") {
                val app = application as MidoriApplication
                app.initializeBrowser("diagnostics_probe", restoreSession = false)
                val view = GeckoView(this@SentryProbeActivity)
                setContentView(view)
                geckoSession = GeckoSession().apply {
                    contentDelegate = object : GeckoSession.ContentDelegate {
                        override fun onCrash(session: GeckoSession) {
                            File(filesDir, "gecko-crash-probe.json").writeText(JSONObject(mapOf(
                                "content_crashed" to true, "parent_pid" to Process.myPid(),
                            )).toString())
                            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 2_000)
                        }
                    }
                    open(app.geckoRuntime.get())
                    view.setSession(this)
                    setActive(true)
                    loadUri("about:crashcontent")
                }
                return@launch
            }
            if (probe == "replay-native") {
                withContext(Dispatchers.IO) {
                    repeat(40) {
                        if (inspectNativeEnvelope()) return@withContext
                        delay(250)
                    }
                }
                finish()
                return@launch
            }
            Handler(Looper.getMainLooper()).postDelayed({
                when (probe) {
                    "native-crash" -> Process.sendSignal(Process.myPid(), 6)
                    "sigkill" -> Process.killProcess(Process.myPid())
                    "handled-error" -> {
                        io.sentry.Sentry.captureException(IllegalStateException(
                            "diagnostics-private-sentinel https://example.invalid/private?token=secret account@example.invalid",
                        ))
                        finish()
                    }
                    else -> throw SentryTestCrashException()
                }
            }, 1_000)
        }
    }

    override fun onDestroy() {
        geckoSession?.close()
        scope.cancel()
        super.onDestroy()
    }

    private fun inspectNativeEnvelope(): Boolean {
        val options = Sentry.getCurrentScopes().options
        val outbox = options.outboxPath?.let(::File) ?: return false
        for (file in outbox.listFiles().orEmpty().filter { it.isFile }) {
            val envelope = file.inputStream().use { options.envelopeReader.read(it) } ?: continue
            for (item in envelope.items) {
                if (item.header.type != SentryItemType.Event) continue
                val event = options.serializer.deserialize(StringReader(item.data.toString(Charsets.UTF_8)), SentryEvent::class.java)
                    ?: continue
                if (event.platform != "native") continue
                Sentry.captureEvent(event, HintUtils.createWithTypeCheckHint(object : Cached {}))
                val directory = (application as MidoriApplication).diagnostics.get().cacheDirectory
                File(directory, "native-probe.json").writer().use { options.serializer.serialize(event, it) }
                return true
            }
        }
        return false
    }
}

internal class SentryTestCrashException : RuntimeException()
