package org.midorinext.android

import android.os.Process
import android.os.SystemClock
import android.os.Trace
import android.util.Log
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SpanStatus

internal object StartupTiming {
    fun mark(stage: String) {
        Log.i("MidoriStartup", "$stage at ${SystemClock.uptimeMillis() - Process.getStartUptimeMillis()} ms")
        org.midorinext.android.diagnostics.DiagnosticsRuntime.startup(stage.substringBefore(' '))
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "midori.startup"
            message = stage.substringBefore(' ')
        })
    }

    inline fun <T> measure(stage: String, operation: () -> T): T {
        org.midorinext.android.diagnostics.DiagnosticsRuntime.startup(stage)
        val startedAt = SystemClock.uptimeMillis()
        val span = Sentry.getSpan()?.startChild("app.init", stage)
            ?: Sentry.startTransaction("Midori.$stage", "app.init")
        Trace.beginSection("Midori.$stage")
        try {
            return operation()
        } catch (error: Throwable) {
            span.status = SpanStatus.INTERNAL_ERROR
            throw error
        } finally {
            span.finish()
            Trace.endSection()
            Log.i("MidoriStartup", "$stage took ${SystemClock.uptimeMillis() - startedAt} ms")
        }
    }
}
