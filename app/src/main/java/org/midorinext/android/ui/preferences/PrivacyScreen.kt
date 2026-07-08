package org.midorinext.android.ui.preferences

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.preferences.app.*
import org.midorinext.android.ui.preferences.widgets.*
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun PrivacyScreen(
    onClose: () -> Unit,
    onNavigateToAppTrackingReport: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val systemProtectionRunning by viewModel.systemProtectionRunning.collectAsState()
    val appTrackingMetrics by viewModel.appTrackingMetrics.collectAsState()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.startSystemProtection()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = "Privacy and Security", scrollableState = scrollState)

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp)
        ) {
            // Privacy Introduction Banner (optional)
            /*Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Midori protects you from many of the most common trackers that follow what you do online.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }*/

            // Enhanced Tracking Protection Section
            PreferenceGroupLabel(label = R.string.settings_group_privacy)

            PreferenceRadioSelectionPopupWithDescription(
                label = R.string.settings_enhanced_tracking_protection_label,
                options = remember { listOf(
                    RadioButtonOptionWithDescription(TrackingProtectionLevel.STANDARD, R.string.settings_tracking_protection_standard, R.string.settings_tracking_protection_standard_desc),
                    RadioButtonOptionWithDescription(TrackingProtectionLevel.STRICT, R.string.settings_tracking_protection_strict, R.string.settings_tracking_protection_strict_desc),
                    RadioButtonOptionWithDescription(TrackingProtectionLevel.CUSTOM, R.string.settings_tracking_protection_custom, R.string.settings_tracking_protection_custom_desc)
                ) },
                value = appPrefs.trackingProtectionLevel,
                onValueChange = { viewModel.updateTrackingProtectionLevel(it) }
            )

            // HTTPS-Only Mode Section
            PreferenceRadioSelectionPopupWithDescription(
                label = R.string.settings_https_only_mode_label,
                options = remember { listOf(
                    RadioButtonOptionWithDescription(HttpsOnlyLevel.OFF, R.string.settings_https_only_off, R.string.settings_https_only_off_desc),
                    RadioButtonOptionWithDescription(HttpsOnlyLevel.ALL_TABS, R.string.settings_https_only_all_tabs),
                    RadioButtonOptionWithDescription(HttpsOnlyLevel.PRIVATE_TABS, R.string.settings_https_only_private_tabs)
                ) },
                value = appPrefs.httpsOnlyLevel,
                onValueChange = { viewModel.updateHttpsOnlyLevel(it) }
            )

            // DNS over HTTPS Section
            PreferenceRadioSelectionPopupWithDescription(
                label = R.string.settings_dns_over_https_label,
                options = remember { listOf(
                    RadioButtonOptionWithDescription(DoHProvider.DOH_DEFAULT, R.string.settings_dns_over_https_default, R.string.settings_dns_over_https_default_desc),
                    RadioButtonOptionWithDescription(DoHProvider.DOH_INCREASED_PROTECTION, R.string.settings_dns_over_https_increased, R.string.settings_dns_over_https_increased_desc),
                    RadioButtonOptionWithDescription(DoHProvider.DOH_MAX_PROTECTION, R.string.settings_dns_over_https_max, R.string.settings_dns_over_https_max_desc),
                    RadioButtonOptionWithDescription(DoHProvider.DOH_OFF, R.string.settings_https_only_off)
                ) },
                value = appPrefs.dohProvider,
                onValueChange = { viewModel.updateDohProvider(it) }
            )

            // Additional Privacy Features
            PreferenceGroupLabel(label = R.string.settings_group_privacy)

            PreferenceToggle(
                label = R.string.privacy_setting_app_tracking_protection_label,
                description = R.string.privacy_setting_app_tracking_protection_desc,
                value = appPrefs.privacyStrictTrackingProtection,
                onValueChange = { viewModel.updateStrictTrackingProtection(it) }
            )

            PreferenceRadioSelectionPopupWithDescription(
                label = R.string.privacy_setting_app_tracking_protection_mode_label,
                options = remember { listOf(
                    RadioButtonOptionWithDescription(
                        AppTrackingProtectionMode.BROWSER_FIRST,
                        R.string.privacy_setting_app_tracking_protection_mode_browser_first,
                        R.string.privacy_setting_app_tracking_protection_mode_browser_first_desc
                    ),
                    RadioButtonOptionWithDescription(
                        AppTrackingProtectionMode.HYBRID_SYSTEM,
                        R.string.privacy_setting_app_tracking_protection_mode_hybrid_system,
                        R.string.privacy_setting_app_tracking_protection_mode_hybrid_system_desc
                    )
                ) },
                value = appPrefs.appTrackingProtectionMode,
                onValueChange = { viewModel.updateAppTrackingProtectionMode(it) }
            )

            if (appPrefs.appTrackingProtectionMode == AppTrackingProtectionMode.HYBRID_SYSTEM) {
                val statusDescription = if (systemProtectionRunning) {
                    stringResource(R.string.privacy_setting_app_tracking_system_status_running)
                } else {
                    stringResource(R.string.privacy_setting_app_tracking_system_status_stopped)
                }

                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_system_status_label,
                    description = statusDescription
                )

                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_open_report_label,
                    description = stringResource(R.string.privacy_setting_app_tracking_open_report_desc),
                    onClicked = onNavigateToAppTrackingReport
                )

                PreferenceToggle(
                    label = R.string.privacy_setting_app_tracking_system_enabled_label,
                    description = R.string.privacy_setting_app_tracking_system_enabled_desc,
                    value = appPrefs.appTrackingSystemEnabled && systemProtectionRunning,
                    onValueChange = { enabled ->
                        if (enabled) {
                            val permissionIntent = viewModel.getSystemProtectionPermissionIntent()
                            if (permissionIntent != null) {
                                vpnPermissionLauncher.launch(permissionIntent)
                            } else {
                                viewModel.startSystemProtection()
                            }
                        } else {
                            viewModel.stopSystemProtection()
                        }
                    }
                )

                val appPackageName = BuildConfig.APPLICATION_ID
                val appExcluded = appPrefs.appTrackingExcludedPackagesList.contains(appPackageName)
                PreferenceToggle(
                    label = R.string.privacy_setting_app_tracking_exclude_midori_label,
                    description = R.string.privacy_setting_app_tracking_exclude_midori_desc,
                    value = appExcluded,
                    onValueChange = { excluded ->
                        if (excluded) {
                            viewModel.addAppTrackingExcludedPackage(appPackageName)
                        } else {
                            viewModel.removeAppTrackingExcludedPackage(appPackageName)
                        }
                    }
                )

                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_metrics_total_label,
                    description = appTrackingMetrics.totalBlockedRequests.toString()
                )

                val topDomain = appTrackingMetrics.blockedByDomain.maxByOrNull { it.value }
                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_metrics_top_domain_label,
                    description = topDomain?.let { "${it.key} (${it.value})" }
                        ?: stringResource(R.string.privacy_setting_app_tracking_metrics_none)
                )

                val topApp = appTrackingMetrics.blockedByApp.maxByOrNull { it.value }
                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_metrics_top_app_label,
                    description = topApp?.let { "${it.key} (${it.value})" }
                        ?: stringResource(R.string.privacy_setting_app_tracking_metrics_none)
                )

                val topCompany = appTrackingMetrics.blockedByCompany.maxByOrNull { it.value }
                PreferenceRow(
                    label = R.string.privacy_setting_app_tracking_metrics_top_company_label,
                    description = topCompany?.let { "${it.key} (${it.value})" }
                        ?: stringResource(R.string.privacy_setting_app_tracking_metrics_none)
                )

                val detailRows = appTrackingMetrics.blockedDetails
                if (detailRows.isNotEmpty()) {
                    PreferenceGroupLabel(label = R.string.privacy_setting_app_tracking_recent_label)

                    val maxCount = detailRows.maxOf { it.blockedCount }.coerceAtLeast(1)
                    detailRows.take(5).forEach { detail ->
                        val progress = detail.blockedCount.toFloat() / maxCount.toFloat()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${detail.trackerCompany} - ${detail.blockedCount}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${detail.appPackage} - ${detail.trackerDomain}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = detail.trackerCategories.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = detail.blockedCount.toString(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            PreferenceToggle(
                label = R.string.privacy_setting_fingerprinting_label,
                value = appPrefs.privacyFingerprintingProtection,
                onValueChange = { viewModel.updateFingerprintingProtection(it) }
            )

            PreferenceToggle(
                label = R.string.privacy_setting_gpc_label,
                value = appPrefs.privacyGlobalPrivacyControl,
                onValueChange = { viewModel.updateGlobalPrivacyControl(it) }
            )

            PreferenceToggle(
                label = R.string.privacy_setting_cookie_partitioning_label,
                value = appPrefs.privacyCookiePartitioning,
                onValueChange = { viewModel.updateCookiePartitioning(it) }
            )
        }
    }
}

