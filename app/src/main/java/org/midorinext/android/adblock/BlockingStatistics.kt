package org.midorinext.android.adblock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    private val counter = BlockingStatisticsCounter()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val mutableTotal = MutableStateFlow(0L)
    val totalBlocked = mutableTotal.asStateFlow()

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val preferences = context.getSharedPreferences("native_blocking_statistics", Context.MODE_PRIVATE)
            val savedTotal = preferences.getLong("total_blocked", 0).coerceAtLeast(0)
            mutableTotal.value = synchronized(counter) {
                counter.record(savedTotal)
                counter.totalBlocked
            }
            if (mutableTotal.value != savedTotal) {
                preferences.edit().putLong("total_blocked", mutableTotal.value).apply()
            }
            for (change in changes) {
                delay(250)
                val total = synchronized(counter) { counter.totalBlocked }
                if (mutableTotal.value != total) {
                    preferences.edit().putLong("total_blocked", total).apply()
                    mutableTotal.value = total
                }
            }
        }
    }

    fun recordBlocked() {
        synchronized(counter) { counter.record() }
        changes.trySend(Unit)
    }

    fun recordNetworkSnapshot(reportId: String, count: Long) {
        synchronized(counter) { counter.recordNetwork(reportId, count) }
        changes.trySend(Unit)
    }
}
