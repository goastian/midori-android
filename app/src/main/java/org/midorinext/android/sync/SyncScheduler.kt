package org.midorinext.android.sync

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.*
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

internal object SyncScheduler {
    const val BACKGROUND_MINUTES = 30L
    const val FOREGROUND_MINUTES = 15L
    const val LOCAL_DEBOUNCE_MS = 2_000L
    private val started = java.util.concurrent.atomic.AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    fun start(context: Context, coordinator: SyncCoordinator) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try { coordinator.inspect() } catch (error: CancellationException) { throw error } catch (_: Exception) { }
            coordinator.ui.map { it.connected to it.accountId }.distinctUntilChanged().collectLatest { (connected, account) ->
                val manager = WorkManager.getInstance(appContext)
                manager.cancelUniqueWork("midori-sync-local")
                if (!connected) {
                    manager.cancelUniqueWork("midori-sync")
                    return@collectLatest
                }
                val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                manager.enqueueUniquePeriodicWork("midori-sync", ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<SyncWorker>(BACKGROUND_MINUTES, TimeUnit.MINUTES)
                        .setInitialDelay(BACKGROUND_MINUTES, TimeUnit.MINUTES)
                        .setInputData(workDataOf("account" to account)).setConstraints(constraints)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
                coroutineScope {
                    suspend fun request(reason: SyncReason) {
                        try {
                            coordinator.sync(reason)
                            if (reason == SyncReason.LOCAL && coordinator.needsLocalSync()) SyncLocalSignals.changed()
                        }
                        catch (error: CancellationException) { throw error }
                        catch (_: Exception) { enqueueLocal(appContext, account, 30_000) }
                    }
                    launch {
                        val otherPending = java.util.concurrent.atomic.AtomicBoolean()
                        merge(coordinator.localChanges().map { Unit }, SyncLocalSignals.changes.onEach { otherPending.set(true) })
                            .debounce(LOCAL_DEBOUNCE_MS).collect {
                                if (!otherPending.getAndSet(false) && !coordinator.needsLocalSync()) return@collect
                                if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) request(SyncReason.LOCAL)
                                else enqueueLocal(appContext, account)
                            }
                    }
                    launch(Dispatchers.Main) {
                        ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            launch {
                                if (System.currentTimeMillis() - coordinator.ui.value.lastSync >= TimeUnit.MINUTES.toMillis(FOREGROUND_MINUTES)) request(SyncReason.RETURN)
                                while (isActive) {
                                    delay(TimeUnit.MINUTES.toMillis(FOREGROUND_MINUTES))
                                    request(SyncReason.PERIODIC)
                                }
                            }
                            launch(Dispatchers.IO) {
                                coordinator.ui.map { it.phase == "ready" }.distinctUntilChanged().collectLatest { ready ->
                                    if (ready) watch(coordinator) { request(SyncReason.REMOTE) }
                                }
                            }
                        }
                    }
                    awaitCancellation()
                }
            }
        }
    }

    private fun enqueueLocal(context: Context, account: String, delayMs: Long = LOCAL_DEBOUNCE_MS) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf("account" to account))
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("midori-sync-local", ExistingWorkPolicy.KEEP, request)
    }

    @OptIn(FlowPreview::class)
    private suspend fun watch(coordinator: SyncCoordinator, changed: suspend () -> Unit): Unit = coroutineScope {
        val hints = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        val consumer = launch { hints.debounce(1_000).collect { changed() } }
        var failures = 0
        try {
            while (isActive) {
                var retry = 1_000L
                try {
                    val (server, ticket) = coordinator.notificationTicket()
                    val openedAt = android.os.SystemClock.elapsedRealtime()
                    coroutineScope {
                        val network = SyncNetworkContext(currentCoroutineContext().job)
                        val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() } finally { network.cancel() }
                        }
                        try { withContext(Dispatchers.IO) { SyncTransport(server, network).watchNotifications(ticket) { hints.tryEmit(Unit) } } }
                        finally { cancellation.cancel() }
                    }
                    if (android.os.SystemClock.elapsedRealtime() - openedAt < 10_000) throw SyncException("notification_closed")
                    failures = 0
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) {
                    retry = if ((error as? SyncException)?.status in listOf(404, 405)) TimeUnit.MINUTES.toMillis(15)
                        else minOf(60_000L, 1_000L shl (++failures).coerceAtMost(6))
                }
                delay(retry)
            }
        } finally { consumer.cancel() }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SyncWorkerEntryPoint { fun syncCoordinator(): SyncCoordinator }

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.WORK_EXECUTED)
        val coordinator = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java).syncCoordinator()
        return try {
            coordinator.inspect()
            if (!coordinator.ui.value.connected || inputData.getString("account") != coordinator.ui.value.accountId) return Result.success()
            coordinator.sync(SyncReason.PERIODIC)
            Result.success()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            if ((error as? SyncException)?.status == 401) Result.failure() else Result.retry()
        }
    }
}
