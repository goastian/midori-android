package org.midorinext.android.mozac

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

internal data class MemoryProfile(val name: String, val foreground: Int, val background: Int) {
    val foregroundUnderPressure: Int get() = background.coerceAtLeast(1)

    companion object {
        fun forDevice(lowRam: Boolean, ramMiB: Long, memoryClass: Int): MemoryProfile {
            val background = if (lowRam || ramMiB <= 4096) 0 else 1
            return when {
                lowRam || ramMiB <= 2048 || memoryClass <= 128 -> MemoryProfile("compact", 3, background)
                ramMiB <= 4096 || memoryClass <= 256 -> MemoryProfile("balanced", 4, background)
                else -> MemoryProfile("large", 5, background)
            }
        }
    }
}

internal class MemoryBudgetPolicy(
    val profile: MemoryProfile,
    private val now: () -> Long,
) {
    var visible = false
        private set
    @Volatile private var activitiesStopped = false
    private var constrainedUntil = 0L
    val canSuspendIdleSelection: Boolean get() = activitiesStopped && profile.background == 0
    val limit: Int get() = when {
        activitiesStopped -> profile.background
        visible && now() >= constrainedUntil -> profile.foreground
        else -> profile.foregroundUnderPressure
    }

    fun visibility(value: Boolean) {
        activitiesStopped = !value
        visible = value
    }

    fun trim(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            visible = false
        }
        if (level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW until ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            constrainedUntil = now() + 30_000
        }
    }
}

internal fun iconCacheTrimLevel(level: Int) = if (shouldClearBrowserMemory(level)) ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL else level

internal fun shouldClearBrowserMemory(level: Int) = level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
    level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
    level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW until ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN

@Singleton
class BrowserMemoryBudget @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    internal val profile = MemoryProfile.forDevice(manager.isLowRamDevice, info.totalMem / (1024 * 1024), manager.memoryClass)
    internal val policy = MemoryBudgetPolicy(profile, SystemClock::elapsedRealtime)
    private data class Owner(val changed: () -> Unit, var tabs: List<ResidentTab> = emptyList())
    private val owners = linkedMapOf<Any, Owner>()
    private val caches = mutableListOf<(Int?) -> Boolean>()
    private var lastException = ""
    private val pressureMonitor = MemoryPressureMonitor(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        underPressure = {
            withContext(Dispatchers.IO) {
                val lowMemory = try {
                    ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory
                } catch (error: RuntimeException) {
                    android.util.Log.w("MidoriMemory", "Memory pressure information unavailable", error)
                    false
                }
                val runtime = Runtime.getRuntime()
                org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.MEMORY_CHECKED)
                lowMemory || runtime.totalMemory() - runtime.freeMemory() > runtime.maxMemory() * 4 / 5
            }
        },
        onPressure = {
            org.midorinext.android.diagnostics.DiagnosticsRuntime.memoryPressure(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
            onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        },
    )

    internal fun register(owner: Any, changed: () -> Unit) {
        owners[owner] = Owner(changed)
        if (policy.visible) pressureMonitor.start()
    }
    internal fun unregister(owner: Any) {
        owners.remove(owner)
        if (owners.isEmpty()) pressureMonitor.stop()
        notifyOwners()
    }
    internal fun update(owner: Any, tabs: List<ResidentTab>) {
        val entry = owners[owner] ?: return
        if (entry.tabs == tabs) return
        entry.tabs = tabs
        notifyOwners()
    }

    internal fun candidates(owner: Any): List<String> {
        val all = owners.values.flatMap { it.tabs }
        val victims = tabsToSuspend(all, policy.limit).toSet()
        val protected = all.count { it.protected }
        val exception = if (protected > policy.limit) "profile=${profile.name} resident=${all.size} protected=$protected limit=${policy.limit}" else ""
        if (exception != lastException) {
            lastException = exception
            if (exception.isNotEmpty()) android.util.Log.i("MidoriMemory", "protected-budget-exception $exception")
        }
        return owners[owner]?.tabs.orEmpty().map { it.id }.filter { it in victims }
    }

    fun <T : Any> registerCache(owner: T, clear: (T, Int) -> Unit) {
        val reference = WeakReference(owner)
        synchronized(caches) {
            caches.removeAll { !it(null) }
            caches += { level ->
                val value = reference.get()
                if (value != null && level != null) clear(value, level)
                value != null
            }
        }
    }

    fun onVisibilityChanged(visible: Boolean) {
        policy.visibility(visible)
        if (visible && owners.isNotEmpty()) pressureMonitor.start() else pressureMonitor.stop()
        if (!visible) {
            trimCaches(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        }
        notifyOwners()
    }

    fun onTrimMemory(level: Int) {
        policy.trim(level)
        if (!policy.visible) pressureMonitor.stop()
        trimCaches(level)
        notifyOwners()
    }

    private fun trimCaches(level: Int) {
        synchronized(caches) { caches.removeAll { !it(iconCacheTrimLevel(level)) } }
    }

    private fun notifyOwners() { owners.values.toList().forEach { it.changed() } }
}
