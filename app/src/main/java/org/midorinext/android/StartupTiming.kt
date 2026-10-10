package org.midorinext.android

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log

internal object StartupTiming {
    fun mark(stage: String) {
        Log.i("MidoriStartup", "$stage at ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()} ms")
    }

    inline fun <T> measure(stage: String, operation: () -> T): T {
        val startedAt = SystemClock.uptimeMillis()
        Trace.beginSection("Midori.$stage")
        try {
            return operation()
        } finally {
            Trace.endSection()
            Log.i("MidoriStartup", "$stage took ${SystemClock.uptimeMillis() - startedAt} ms")
        }
    }
}
