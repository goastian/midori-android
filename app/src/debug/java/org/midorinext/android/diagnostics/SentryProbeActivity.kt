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

class SentryProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = intent.getStringExtra("probe")
        if (probe !in setOf("crash", "native-crash", "handled-error", "sigkill", "disable", "replay-native")) {
            finish()
            return
        }
        scope.launch {
            (application as MidoriApplication).diagnostics.get().setEnabled(probe != "disable").join()
            if (probe == "disable") {
                finish()
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
