package org.midorinext.android.adblock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

internal class BlockingStatisticsCounter(initialTotal: Long = 0) {
    var totalBlocked = initialTotal.coerceAtLeast(0)
        private set
    private val networkReports = linkedMapOf<String, Long>()

    fun record(count: Long = 1) {
        if (count > 0) totalBlocked += minOf(count, Long.MAX_VALUE - totalBlocked)
    }

    fun recordNetwork(reportId: String, count: Long) {
        if (reportId.isBlank() || reportId.length > 128 || count < 0) return
        val previous = networkReports[reportId] ?: 0
        if (count <= previous) return
        networkReports[reportId] = count
        if (networkReports.size > 32) networkReports.remove(networkReports.keys.first())
        record(count - previous)
    }
}

@Singleton
class BlockingStatistics @Inject constructor(@ApplicationContext context: Context) {
    private sealed interface Event {
        data class Block(val count: Long) : Event
        data class Network(val reportId: String, val count: Long) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val mutableTotal = MutableStateFlow(0L)
    val totalBlocked = mutableTotal.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = context.getSharedPreferences("native_blocking_statistics", Context.MODE_PRIVATE)
            val counter = BlockingStatisticsCounter(preferences.getLong("total_blocked", 0))
            mutableTotal.value = counter.totalBlocked
            for (event in events) {
                fun apply(event: Event) = when (event) {
                    is Event.Block -> counter.record(event.count)
                    is Event.Network -> counter.recordNetwork(event.reportId, event.count)
                }
                apply(event)
                while (true) apply(events.tryReceive().getOrNull() ?: break)
                if (mutableTotal.value != counter.totalBlocked) {
                    preferences.edit().putLong("total_blocked", counter.totalBlocked).apply()
                    mutableTotal.value = counter.totalBlocked
                }
            }
        }
    }

    fun recordBlocked() {
        events.trySend(Event.Block(1))
    }

    fun recordNetworkSnapshot(reportId: String, count: Long) {
        events.trySend(Event.Network(reportId, count))
    }
}
