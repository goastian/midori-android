package org.midorinext.android.storage.history

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HistoryMaintenanceEntryPoint {
    fun historyRepository(): HistoryRepository
}

class HistoryMaintenanceWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        org.midorinext.android.diagnostics.ScenarioMetrics.count(org.midorinext.android.diagnostics.ScenarioMetrics.Event.WORK_EXECUTED)
        val preferences = applicationContext.getSharedPreferences("history_maintenance", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = preferences.getLong("last_success", 0)
        if (now >= last && now - last < TimeUnit.DAYS.toMillis(1)) return Result.success()
        EntryPointAccessors.fromApplication(applicationContext, HistoryMaintenanceEntryPoint::class.java)
            .historyRepository().runMaintenance(0U)
        preferences.edit().putLong("last_success", System.currentTimeMillis()).commit()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HistoryMaintenanceWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "midori-history-maintenance", ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
