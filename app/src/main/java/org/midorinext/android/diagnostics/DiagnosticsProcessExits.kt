package org.midorinext.android.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.hints.Backfillable
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import io.sentry.util.HintUtils
import java.util.Date
import kotlin.math.abs
import org.midorinext.android.BuildConfig

internal class DiagnosticsProcessExits(private val context: Context, private val preferences: SharedPreferences) {
    private val release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"

    fun startSession() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val previousPid = preferences.getInt("exit.pid", 0)
        val previousStart = preferences.getLong("exit.started_at", 0)
        val previousRelease = preferences.getString("exit.release", null)
        val previousDist = preferences.getString("exit.dist", null)
        if (previousPid == Process.myPid() && previousStart == DiagnosticsRuntime.processStartedAt) return
        val exit = try {
            if (previousPid == 0 || previousStart == 0L) null else previousExit(previousPid, previousStart)
        } catch (error: RuntimeException) {
            Log.w("MidoriDiagnostics", "Process exit history unavailable: ${error.javaClass.simpleName}")
            null
        }
        if (!preferences.edit()
                .putInt("exit.pid", Process.myPid())
                .putLong("exit.started_at", DiagnosticsRuntime.processStartedAt)
                .putString("exit.release", release)
                .putString("exit.dist", BuildConfig.VERSION_CODE.toString())
                .commit()) return
        if (exit != null && previousRelease != null) report(exit, previousRelease, previousDist)
    }

    fun clear() {
        preferences.edit().remove("exit.pid").remove("exit.started_at")
            .remove("exit.release").remove("exit.dist").commit()
    }

    fun enrich(event: SentryEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || event.platform != "native" ||
            event.timestamp.time >= DiagnosticsRuntime.processStartedAt) return
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val exit = manager.getHistoricalProcessExitReasons(null, 0, 16).filter {
                it.processName == context.packageName && abs(it.timestamp - event.timestamp.time) <= 10_000
            }.minByOrNull { abs(it.timestamp - event.timestamp.time) } ?: return
            event.contexts["android_process_exit"] = exitContext(exit)
        } catch (error: RuntimeException) {
            Log.w("MidoriDiagnostics", "Native exit context unavailable: ${error.javaClass.simpleName}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun previousExit(pid: Int, startedAt: Long): ApplicationExitInfo? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getHistoricalProcessExitReasons(null, pid, 8).firstOrNull {
            it.processName == context.packageName && it.timestamp >= startedAt &&
                it.timestamp < DiagnosticsRuntime.processStartedAt
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun report(exit: ApplicationExitInfo, previousRelease: String, previousDist: String?) {
        val type = when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "AndroidLowMemoryKill"
            ApplicationExitInfo.REASON_SIGNALED -> if (exit.status == 9) "AndroidProcessSigkill" else return
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "AndroidResourceLimit"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "AndroidInitializationFailure"
            else -> return
        }
        val event = SentryEvent().apply {
            timestamp = Date(exit.timestamp)
            release = previousRelease
            dist = previousDist
            environment = if (BuildConfig.DEBUG) "development" else "production"
            level = SentryLevel.WARNING
            exceptions = listOf(SentryException().apply {
                this.type = type
                module = "org.midorinext.android.diagnostics"
                mechanism = Mechanism().apply {
                    this.type = "android.process.exit"
                    isHandled = true
                }
            })
            contexts["android_process_exit"] = exitContext(exit)
        }
        // The reporting process has a different lifecycle and memory state.
        Sentry.captureEvent(event, HintUtils.createWithTypeCheckHint(HistoricalExit()))
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun exitContext(exit: ApplicationExitInfo) = mapOf(
        "reason_code" to exit.reason,
        "reason" to when (exit.reason) {
            ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
            ApplicationExitInfo.REASON_SIGNALED -> "signaled"
            ApplicationExitInfo.REASON_CRASH -> "java_crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource_usage"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
            else -> "other"
        },
        "status" to exit.status,
        "importance" to exit.importance,
        "process_state" to when (exit.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "foreground"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "foreground_service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "perceptible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "cached"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE -> "gone"
            else -> "other"
        },
        "low_memory_kill_report_supported" to ActivityManager.isLowMemoryKillReportSupported(),
        "last_sample_pss_kib" to exit.pss,
        "last_sample_rss_kib" to exit.rss,
        "process_role" to "browser",
        "collected_on_next_start" to true,
    )

    private class HistoricalExit : Backfillable {
        override fun shouldEnrich() = false
    }
}
