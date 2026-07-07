package org.midorinext.android.apptracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppTrackingMetrics(
    val totalBlockedRequests: Int = 0,
    val blockedByDomain: Map<String, Int> = emptyMap(),
    val blockedByApp: Map<String, Int> = emptyMap(),
    val blockedByCompany: Map<String, Int> = emptyMap(),
    val blockedDetails: List<BlockedTrackerDetail> = emptyList()
)

data class BlockedTrackerDetail(
    val appPackage: String,
    val trackerDomain: String,
    val trackerCompany: String,
    val trackerCategories: List<String>,
    val blockedCount: Int,
    val lastBlockedAtMillis: Long = 0L
)

object AppTrackingRuntimeState {
    private val mutableRunning = MutableStateFlow(false)
    val running: StateFlow<Boolean> = mutableRunning.asStateFlow()

    private val mutableMetrics = MutableStateFlow(AppTrackingMetrics())
    val metrics: StateFlow<AppTrackingMetrics> = mutableMetrics.asStateFlow()

    fun setRunning(isRunning: Boolean) {
        mutableRunning.value = isRunning
    }

    fun resetMetrics() {
        mutableMetrics.value = AppTrackingMetrics()
    }

    fun recordBlocked(
        domain: String,
        appPackage: String?,
        company: String,
        categories: List<String>
    ) {
        val now = System.currentTimeMillis()
        val current = mutableMetrics.value
        val nextDomainMap = current.blockedByDomain.toMutableMap().also { map ->
            map[domain] = (map[domain] ?: 0) + 1
        }
        val packageName = appPackage ?: "unknown"
        val nextAppMap = current.blockedByApp.toMutableMap().also { map ->
            map[packageName] = (map[packageName] ?: 0) + 1
        }
        val nextCompanyMap = current.blockedByCompany.toMutableMap().also { map ->
            map[company] = (map[company] ?: 0) + 1
        }

        val normalizedCategories = categories.sorted()
        val nextDetails = current.blockedDetails.toMutableList()
        val index = nextDetails.indexOfFirst {
            it.appPackage == packageName &&
                it.trackerDomain == domain &&
                it.trackerCompany == company &&
                it.trackerCategories == normalizedCategories
        }
        if (index >= 0) {
            val detail = nextDetails[index]
            nextDetails[index] = detail.copy(
                blockedCount = detail.blockedCount + 1,
                lastBlockedAtMillis = now
            )
        } else {
            nextDetails += BlockedTrackerDetail(
                appPackage = packageName,
                trackerDomain = domain,
                trackerCompany = company,
                trackerCategories = normalizedCategories,
                blockedCount = 1,
                lastBlockedAtMillis = now
            )
        }

        val trimmedDetails = nextDetails
            .sortedByDescending { it.lastBlockedAtMillis }
            .take(MAX_TRACKER_DETAILS)

        mutableMetrics.value = current.copy(
            totalBlockedRequests = current.totalBlockedRequests + 1,
            blockedByDomain = nextDomainMap,
            blockedByApp = nextAppMap,
            blockedByCompany = nextCompanyMap,
            blockedDetails = trimmedDetails
        )
    }

    private const val MAX_TRACKER_DETAILS = 25
}



