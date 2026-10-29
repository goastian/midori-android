package org.midorinext.android.mozac

import android.content.Context
import android.os.Looper
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mozilla.components.feature.addons.AddonManager
import org.midorinext.android.MidoriApplication

internal class BrowserWorkerFactory(
    private val application: MidoriApplication,
    private val addonManager: dagger.Lazy<AddonManager>,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        if (workerClassName == "mozilla.components.feature.addons.update.AddonUpdaterWorker") {
            fun prepare() {
                application.initializeBrowser("addon_update_worker", restoreSession = false)
                addonManager.get()
            }
            if (Looper.myLooper() == Looper.getMainLooper()) prepare()
            else runBlocking(Dispatchers.Main) { prepare() }
        }
        return null
    }
}
