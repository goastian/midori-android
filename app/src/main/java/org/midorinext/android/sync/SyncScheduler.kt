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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

internal object SyncScheduler {
    private var started = false
    fun start(context: Context, coordinator: SyncCoordinator) {
        if (started) return
        started = true
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { coordinator.inspect() }
            coordinator.ui.map { it.connected }.distinctUntilChanged().collect { connected ->
                val manager = WorkManager.getInstance(context)
                if (connected) manager.enqueueUniquePeriodicWork("midori-sync", ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
                else manager.cancelUniqueWork("midori-sync")
            }
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            ProcessLifecycleOwner.get().lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (coordinator.ui.value.connected) runCatching { coordinator.sync() }
                    delay(30_000)
                }
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SyncWorkerEntryPoint { fun syncCoordinator(): SyncCoordinator }

class SyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.WORK_EXECUTED)
        val coordinator = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java).syncCoordinator()
        return runCatching { coordinator.sync(); Result.success() }.getOrElse { Result.retry() }
    }
}
