package org.midorinext.android.apptracking

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppTrackingTelemetry @Inject constructor() {
    fun logEvent(event: String) {
        Log.i(LOG_TAG, "event=$event")
    }

    fun logTrafficSample(rxBytes: Long, txBytes: Long) {
        Log.i(LOG_TAG, "traffic_sample rxBytes=$rxBytes txBytes=$txBytes")
    }

    fun logBlockedRequest(
        domain: String,
        appPackage: String?,
        company: String,
        categories: List<String>
    ) {
        AppTrackingRuntimeState.recordBlocked(domain, appPackage, company, categories)
        Log.i(
            LOG_TAG,
            "blocked domain=$domain app=${appPackage ?: "unknown"} company=$company types=${categories.joinToString("|")}"
        )
    }

    companion object {
        private const val LOG_TAG = "AppTrackingTelemetry"
    }
}


