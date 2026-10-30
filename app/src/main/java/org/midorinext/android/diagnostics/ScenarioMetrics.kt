package org.midorinext.android.diagnostics

import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import org.midorinext.android.BuildConfig
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLongArray

internal object ScenarioMetrics {
    enum class Event { SESSION_CREATED, SESSION_CLOSED, SESSION_RESTORED, LOAD_STARTED, SAME_URL_LOAD_STARTED, SYNC_REQUESTED, SYNC_EXECUTED, WORK_EXECUTED }
    enum class Rows { HISTORY, BOOKMARKS, FOLDER_PICKER }
    private val retained = AtomicLongArray(Rows.entries.size)
    private val peakRetained = AtomicLongArray(Rows.entries.size)
    @Volatile var enabled = false
    private val counts = AtomicLongArray(Event.entries.size)
    private val inventories = WeakHashMap<Any, () -> List<Session>>()
    private val generations = WeakHashMap<Any, Long>()
    private val surfaces = WeakHashMap<View, Boolean>()
    private var generation = 0L

    data class Session(val identity: Any?, val recoverable: Boolean, val reasons: List<String>)

    fun count(event: Event, amount: Int = 1) {
        if (BuildConfig.DEBUG && enabled) counts.addAndGet(event.ordinal, amount.toLong())
    }

    fun rows(kind: Rows, amount: Int) {
        if (!BuildConfig.DEBUG || !enabled) return
        retained.set(kind.ordinal, amount.toLong())
        while (true) {
            val peak = peakRetained.get(kind.ordinal)
            if (amount <= peak || peakRetained.compareAndSet(kind.ordinal, peak, amount.toLong())) break
        }
    }

    fun start() {
        check(BuildConfig.DEBUG)
        Event.entries.forEach { counts.set(it.ordinal, 0) }
        Rows.entries.forEach { retained.set(it.ordinal, 0); peakRetained.set(it.ordinal, 0) }
        enabled = true
    }

    fun inventory(owner: Any, snapshot: () -> List<Session>) {
        if (BuildConfig.DEBUG) inventories[owner] = snapshot
    }

    fun remove(owner: Any) { if (BuildConfig.DEBUG) inventories.remove(owner) }
    fun surface(view: View) { if (BuildConfig.DEBUG) surfaces[view] = true }
    fun removeSurface(view: View) { if (BuildConfig.DEBUG) surfaces.remove(view) }

    fun snapshot(): JSONObject {
        val result = JSONObject()
        Event.entries.forEach { result.put(it.name.lowercase(), counts.get(it.ordinal)) }
        Rows.entries.forEach { result.put("${it.name.lowercase()}_retained", retained.get(it.ordinal)).put("${it.name.lowercase()}_peak_retained", peakRetained.get(it.ordinal)) }
        val sessions = inventories.values.toList().flatMap { it() }
        val identities = java.util.IdentityHashMap<Any, Session>()
        sessions.forEach { sample ->
            sample.identity?.let { identity ->
                val previous = identities[identity]
                identities[identity] = if (previous == null) sample else
                    sample.copy(reasons = (previous.reasons + sample.reasons).distinct())
            }
        }
        val active = JSONArray()
        identities.forEach { (identity, sample) ->
            val id = generations.getOrPut(identity) { ++generation }
            active.put(JSONObject().put("session_generation", id).put("protection_reasons", JSONArray(sample.reasons)))
        }
        result.put("logical_tabs", sessions.size).put("resident_sessions", identities.size)
            .put("recoverable_tabs", sessions.count { it.recoverable })
            .put("protected_sessions", identities.values.count { it.reasons.isNotEmpty() })
            .put("sessions", active).put("visible_surfaces", surfaces.keys.count { it.isShown && it.windowVisibility == View.VISIBLE })
        return result
    }
}
