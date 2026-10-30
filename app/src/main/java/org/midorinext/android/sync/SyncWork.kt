package org.midorinext.android.sync

import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class SyncReason { MANUAL, LOCAL, RETURN, PERIODIC, REMOTE }

internal object SyncLocalSignals {
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    fun changed() { changes.tryEmit(Unit) }
}

internal data class SyncRunResult(
    val requests: Long, val sentBytes: Long, val receivedBytes: Long,
    val prepared: Long, val uploaded: Long, val receivedRecords: Long,
    val cpuMs: Long, val elapsedMs: Long, val cancelled: Boolean, val failed: Boolean
) {
    val changed: Boolean get() = uploaded > 0 || receivedRecords > 0
}

internal class SyncRunMetrics {
    val requests = AtomicLong()
    val sentBytes = AtomicLong()
    val receivedBytes = AtomicLong()
    val prepared = AtomicLong()
    val uploaded = AtomicLong()
    val receivedRecords = AtomicLong()
    private val cpu = Process.getElapsedCpuTime()
    private val started = SystemClock.elapsedRealtime()
    fun finish(cancelled: Boolean, failed: Boolean = false) = SyncRunResult(requests.get(), sentBytes.get(), receivedBytes.get(),
        prepared.get(), uploaded.get(), receivedRecords.get(), Process.getElapsedCpuTime() - cpu,
        SystemClock.elapsedRealtime() - started, cancelled, failed)
}

internal class SyncNetworkContext(private val job: Job, val metrics: SyncRunMetrics? = null) {
    private val connections = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    @Volatile private var cancelled = false
    fun check() { job.ensureActive(); if (cancelled) throw CancellationException("Sync connection closed") }
    fun register(connection: HttpURLConnection) { check(); connections.add(connection); if (cancelled) { connection.disconnect(); check() } }
    fun release(connection: HttpURLConnection) { connections.remove(connection); connection.disconnect() }
    fun cancel() { cancelled = true; connections.forEach { it.disconnect() }; connections.clear() }
}
