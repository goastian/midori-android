package org.midorinext.android.diagnostics

import android.os.Process
import android.os.SystemClock
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import org.json.JSONObject

internal object DiagnosticsRuntime {
    private const val NATIVE_SNAPSHOT = "midori_runtime_v2"
    val processStartedAt = System.currentTimeMillis() - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
    @Volatile private var state: Map<String, Any> = emptyMap()
    @Volatile private var currentVisibility = "unknown"
    val startupStages = setOf(
        "application_create", "application_ready", "app_services", "browser_initialization", "browser_ready",
        "preferences_ready", "initial_gecko_settings", "gecko_warm_up", "native_blocker", "first_frame",
        "initial_content_loaded", "maintenance_timeout", "gecko_runtime", "browser_engine",
    )

    fun start() = update(mapOf("visibility" to currentVisibility))

    fun startup(stage: String) {
        if (stage in startupStages) update(mapOf("startup_stage" to stage))
    }

    fun visibility(visible: Boolean) {
        currentVisibility = if (visible) "foreground" else "background"
        update(mapOf("visibility" to currentVisibility))
    }

    fun tabs(count: Int) {
        if (Sentry.isEnabled() && state["tab_count"] != count) update(mapOf("tab_count" to count))
    }

    fun memoryPressure(level: Int) {
        update(mapOf("last_trim_level" to level))
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "midori.memory"
            this.level = SentryLevel.WARNING
            setData("trim_level", level)
        })
    }

    @Synchronized
    private fun update(values: Map<String, Any>) {
        if (!Sentry.isEnabled()) return
        if (values.all { state[it.key] == it.value }) return
        state = state + values
        val snapshot = snapshot()
        Sentry.configureScope {
            it.setContexts("midori_runtime", snapshot)
            // The Android NDK observer synchronizes extras, but not custom contexts.
            it.setExtra(NATIVE_SNAPSHOT, JSONObject(snapshot).toString())
        }
    }

    @Synchronized
    fun clear() { state = emptyMap() }

    @Synchronized
    private fun snapshot(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return state + mapOf(
            "process_role" to "browser",
            "process_started_at_ms" to processStartedAt,
            "visibility" to currentVisibility,
            "sampled_at_process_age_ms" to (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()),
            "java_heap_used_bytes" to (runtime.totalMemory() - runtime.freeMemory()),
            "java_heap_limit_bytes" to runtime.maxMemory(),
        )
    }

    fun enrich(event: SentryEvent) {
        // Cached native crashes and historical ANRs belong to the previous process.
        if (event.timestamp.time >= processStartedAt && event.getTag("diagnostics.source") != "process_exit") {
            event.contexts["midori_runtime"] = snapshot()
        }
    }

    fun restoreNativeSnapshot(event: SentryEvent) {
        if (event.platform != "native" || event.timestamp.time >= processStartedAt) return
        val encoded = event.extras?.get(NATIVE_SNAPSHOT) as? String ?: return
        if (encoded.length > 2_048) return
        val json = try { JSONObject(encoded) } catch (_: org.json.JSONException) { return }
        val startedAt = json.optLong("process_started_at_ms", -1)
        if (startedAt <= 0 || startedAt >= processStartedAt || startedAt > event.timestamp.time) return
        val restored = mutableMapOf<String, Any>("process_role" to "browser", "process_started_at_ms" to startedAt)
        json.optString("startup_stage").takeIf { it in startupStages }?.let { restored["startup_stage"] = it }
        json.optString("visibility").takeIf { it in setOf("foreground", "background", "unknown") }
            ?.let { restored["visibility"] = it }
        for (key in listOf("sampled_at_process_age_ms", "java_heap_used_bytes", "java_heap_limit_bytes", "tab_count", "last_trim_level")) {
            json.optLong(key, -1).takeIf { it >= 0 }?.let { restored[key] = it }
        }
        event.contexts["midori_runtime"] = restored
    }
}
