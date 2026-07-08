package org.midorinext.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.apptracking.BlockedTrackerDetail
import org.midorinext.android.ui.widgets.ScreenHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AppTrackingProtectionReportScreen(
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val appTrackingMetrics by viewModel.appTrackingMetrics.collectAsState()
    val running by viewModel.systemProtectionRunning.collectAsState()

    val protectedApps = remember(appTrackingMetrics.blockedByApp) {
        appTrackingMetrics.blockedByApp
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }
    val disabledApps = remember(appPrefs.appTrackingExcludedPackagesList) {
        appPrefs.appTrackingExcludedPackagesList
            .distinct()
            .take(5)
    }
    val recentTimeline = remember(appTrackingMetrics.blockedDetails) {
        appTrackingMetrics.blockedDetails
            .sortedByDescending { it.lastBlockedAtMillis }
            .take(8)
    }
    val appReports = remember(appTrackingMetrics.blockedDetails) {
        appTrackingMetrics.blockedDetails
            .groupBy { it.appPackage }
            .mapValues { (_, details) -> details.sumOf { it.blockedCount } }
            .toList()
            .sortedByDescending { it.second }
            .take(6)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(
            title = stringResource(R.string.app_tracking_report_title),
            scrollableState = scrollState
        )

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(bottom = 20.dp)
        ) {
            ReportHero(
                totalBlocked = appTrackingMetrics.totalBlockedRequests,
                trackerCompanies = appTrackingMetrics.blockedByCompany.size,
                isRunning = running
            )

            TwoColumnTopAppsCard(
                protectedApps = protectedApps,
                disabledApps = disabledApps
            )

            AppCoverageCard(appReports = appReports)

            TimelineCard(events = recentTimeline)

            TrackerBreakdownCard(details = appTrackingMetrics.blockedDetails)
        }
    }
}

@Composable
private fun ReportHero(totalBlocked: Int, trackerCompanies: Int, isRunning: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.app_tracking_report_signal_board),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isRunning) {
                    stringResource(R.string.app_tracking_report_state_running)
                } else {
                    stringResource(R.string.app_tracking_report_state_stopped)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricPill(
                    label = stringResource(R.string.app_tracking_report_metric_blocked),
                    value = totalBlocked.toString()
                )
                MetricPill(
                    label = stringResource(R.string.app_tracking_report_metric_companies),
                    value = trackerCompanies.toString()
                )
            }
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TwoColumnTopAppsCard(
    protectedApps: List<Pair<String, Int>>,
    disabledApps: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.app_tracking_report_top_apps_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.app_tracking_report_top_protected),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (protectedApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.app_tracking_report_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        protectedApps.forEach { (pkg, count) ->
                            Text(
                                text = "$pkg ($count)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.app_tracking_report_top_disabled),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    if (disabledApps.isEmpty()) {
                        Text(
                            text = stringResource(R.string.app_tracking_report_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        disabledApps.forEach { pkg ->
                            Text(
                                text = pkg,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCoverageCard(appReports: List<Pair<String, Int>>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.app_tracking_report_per_app_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (appReports.isEmpty()) {
                Text(
                    text = stringResource(R.string.app_tracking_report_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                appReports.forEachIndexed { index, (pkg, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${index + 1}. $pkg", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(events: List<BlockedTrackerDetail>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.app_tracking_report_timeline_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (events.isEmpty()) {
                Text(
                    text = stringResource(R.string.app_tracking_report_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.forEachIndexed { index, event ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            if (index < events.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .width(2.dp)
                                        .height(30.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Text(
                                text = "${event.trackerCompany} - ${event.trackerDomain}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${event.appPackage} - ${formatTimelineTime(event.lastBlockedAtMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerBreakdownCard(details: List<BlockedTrackerDetail>) {
    var expandedKeys by remember { mutableStateOf(setOf<String>()) }

    val sortedDetails = remember(details) {
        details.sortedByDescending { it.blockedCount }.take(12)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.app_tracking_report_tracker_breakdown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (sortedDetails.isEmpty()) {
                Text(
                    text = stringResource(R.string.app_tracking_report_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sortedDetails.forEachIndexed { index, detail ->
                    val key = "${detail.appPackage}|${detail.trackerDomain}|${detail.trackerCompany}"
                    val isExpanded = expandedKeys.contains(key)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedKeys = if (isExpanded) {
                                    expandedKeys - key
                                } else {
                                    expandedKeys + key
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${detail.trackerCompany}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = detail.blockedCount.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = detail.trackerDomain,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = detail.appPackage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                detail.trackerCategories.forEach { category ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer)
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (index < sortedDetails.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

private fun formatTimelineTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "-"
    val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestampMillis))
}
