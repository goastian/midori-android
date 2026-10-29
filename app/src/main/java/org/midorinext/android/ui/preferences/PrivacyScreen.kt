package org.midorinext.android.ui.preferences

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.preferences.app.*
import org.midorinext.android.ui.preferences.widgets.*
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun PrivacyScreen(
    onClose: () -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val appPrefs by viewModel.appPreferences.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = stringResource(R.string.settings_group_privacy),
            scrollableState = scrollState,
        )

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
                label = R.string.settings_tracking_protection_strict,
                description = R.string.settings_tracking_protection_strict_desc,
                value = appPrefs.privacyStrictTrackingProtection,
                onValueChange = { viewModel.updateStrictTrackingProtection(it) }
            )

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
