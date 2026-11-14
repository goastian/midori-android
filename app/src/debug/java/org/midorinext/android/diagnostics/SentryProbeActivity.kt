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

class SentryProbeActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val probe = intent.getStringExtra("probe")
        if (probe !in setOf("crash", "native-crash")) {
            finish()
            return
        }
        scope.launch {
            (application as MidoriApplication).diagnostics.get().setEnabled(true).join()
            Handler(Looper.getMainLooper()).postDelayed({
                if (probe == "native-crash") Process.sendSignal(Process.myPid(), 6)
                else throw SentryTestCrashException()
            }, 1_000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal class SentryTestCrashException : RuntimeException()
