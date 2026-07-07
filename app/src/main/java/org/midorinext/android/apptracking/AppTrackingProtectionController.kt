package org.midorinext.android.apptracking

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import org.midorinext.android.preferences.app.AppPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTrackingProtectionController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val telemetry: AppTrackingTelemetry
) {
    val systemProtectionRunning: StateFlow<Boolean> = AppTrackingRuntimeState.running

    fun getVpnPermissionIntent(): Intent? {
        return VpnService.prepare(context)
    }

    suspend fun startSystemProtection() {
        val permissionIntent = VpnService.prepare(context)
        if (permissionIntent != null) {
            telemetry.logEvent("system_protection_permission_required")
            appPreferencesRepository.updateAppTrackingSystemEnabled(false)
            return
        }

        appPreferencesRepository.updateAppTrackingSystemEnabled(true)
        telemetry.logEvent("system_protection_start_requested")
        val intent = Intent(context, MidoriAppTrackingVpnService::class.java).apply {
            action = MidoriAppTrackingVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, intent)
    }

    suspend fun stopSystemProtection() {
        appPreferencesRepository.updateAppTrackingSystemEnabled(false)
        telemetry.logEvent("system_protection_stop_requested")
        context.stopService(Intent(context, MidoriAppTrackingVpnService::class.java))
    }
}


