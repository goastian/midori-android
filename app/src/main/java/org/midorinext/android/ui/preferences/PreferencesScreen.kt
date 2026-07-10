package org.midorinext.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.ext.openAppStorePage
import org.midorinext.android.preferences.app.*
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.preferences.permissions.PermissionsPreference
import org.midorinext.android.ui.preferences.widgets.*
import org.midorinext.android.ui.widgets.HtmlText
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun PreferencesScreen(
    onClose: () -> Unit,
    navigateTo: (NavDestination) -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel(),
    applicationViewModel: MidoriApplicationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val appPrefs by viewModel.appPreferences.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = stringResource(id = R.string.settings), scrollableState = scrollState)

        Column(modifier = Modifier.verticalScroll(scrollState)) {
            // Make default browser
            DefaultBrowserPreference()

            PreferenceGroupLabel(label = R.string.settings_group_general)

            PreferenceRow(
                label = R.string.search_engine_label,
                description = stringResource(id = R.string.search_engine_description),
                trailing = {
                    Text(
                        text = stringResource(id = R.string.search_engine_current_midori),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            )

            SettingsNavRow(
                label = R.string.settings_homepage_title,
                description = openingScreenDescription(appPrefs.homepageOpeningScreen),
                onClicked = { navigateTo(NavDestination.HomepageSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_customize_title,
                description = stringResource(R.string.settings_customize_summary),
                onClicked = { navigateTo(NavDestination.CustomizeSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_passwords_title,
                description = passwordSummary(appPrefs),
                onClicked = { navigateTo(NavDestination.PasswordSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_autofill_title,
                description = autofillSummary(appPrefs),
                onClicked = { navigateTo(NavDestination.AutofillSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_accessibility_title,
                description = accessibilitySummary(appPrefs),
                onClicked = { navigateTo(NavDestination.AccessibilitySettings) }
            )

            PreferenceGroupLabel(label = R.string.settings_group_privacy)

            // Privacy and Security - Main Entry Point
            PreferenceRow(
                label = R.string.settings_group_privacy,
                trailing = { Icon(
                    painter = painterResource(id = R.drawable.icons_arrow_forward),
                    contentDescription = "Open"
                )},
                onClicked = { onNavigateToPrivacy() }
            )

            // Permissions granted to websites editor
            PermissionsPreference(viewModel)
            // Paramètres de suppression de navigation
            ClearDataPreference(viewModel, applicationViewModel)
            // Supprimer les données à la fermeture
            PreferenceToggle(
                label = R.string.clear_data_on_quit_label,
                description = if (appPrefs.clearDataOnQuit) R.string.clear_data_on_quit_description else null,
                value = appPrefs.clearDataOnQuit,
                onValueChange = { viewModel.updateClearDataOnQuit(it) }
            )


            PreferenceGroupLabel(label = R.string.settings_group_about)

            // App details
            AppDetailsPreference()
            // Politique de confidentialité
            val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
            PreferenceRow(
                label = R.string.privacy_policy_label,
                trailing = { Icon(
                    painter = painterResource(id = R.drawable.icons_open),
                    contentDescription = "Open"
                )},
                onClicked = {
                    viewModel.addTabsUseCase(privacyPolicyUrl)
                    onClose()
                }
            )
            // Licence MPL 2.0
            PreferenceSelectionPopup(
                label = R.string.licence_label,
                popupContent = { HtmlText(
                    html = stringResource(id = R.string.settings_licence_content),
                    modifier = Modifier
                        .padding(16.dp)
                ) },
                fullscreenPopup = true
            )
            // Rate us
            PreferenceRow(
                label = R.string.rate_us_label,
                trailing = { Icon(
                    painter = painterResource(id = R.drawable.icons_open),
                    contentDescription = "Open"
                )},
                onClicked = { context.openAppStorePage() }
            )

            // TODO adjust test files by buildtype
            //   using dedicated src path etc ...
            if (BuildConfig.DEBUG) {
                PreferenceGroupLabel(label = R.string.settings_tests_label)

                // Tests prompts feature
                PreferenceRow(
                    label = R.string.settings_tests_prompts_label,
                    trailing = { Icon(
                        painter = painterResource(id = R.drawable.icons_open),
                        contentDescription = "Open"
                    )},
                    onClicked = {
                        viewModel.openTestTabUseCase("prompts")
                        onClose()
                    }
                )
            }
        }
    }
}

@Composable
fun HomepageSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_homepage_title)) {
        PreferenceGroupLabel(label = R.string.settings_homepage_sections)
        PreferenceToggle(
            label = R.string.show_new_tab_home_label,
            description = R.string.show_new_tab_home_description,
            value = !appPrefs.openBlankNewTab,
            onValueChange = viewModel::updateShowNewTabHome
        )
        PreferenceToggle(
            label = R.string.settings_homepage_shortcuts,
            value = appPrefs.homepageShortcutsEnabled,
            onValueChange = viewModel::updateHomepageShortcutsEnabled
        )
        PreferenceToggle(
            label = R.string.settings_homepage_privacy_report,
            value = appPrefs.homepagePrivacyStatsEnabled,
            onValueChange = viewModel::updateHomepagePrivacyStatsEnabled
        )
        PreferenceToggle(
            label = R.string.settings_homepage_weather,
            value = appPrefs.homepageWeatherEnabled,
            onValueChange = viewModel::updateHomepageWeatherEnabled
        )
        PreferenceToggle(
            label = R.string.settings_homepage_wallpaper,
            value = appPrefs.homepageBackgroundPhotoEnabled,
            onValueChange = viewModel::updateHomepageBackgroundPhotoEnabled
        )

        PreferenceGroupLabel(label = R.string.settings_homepage_opening_screen)
        PreferenceRadioSelectionPopupWithDescription(
            label = R.string.settings_homepage_opening_screen,
            options = remember {
                listOf(
                    RadioButtonOptionWithDescription(
                        HomepageOpeningScreen.HOMEPAGE,
                        R.string.settings_homepage_open_homepage,
                    ),
                    RadioButtonOptionWithDescription(
                        HomepageOpeningScreen.LAST_TAB,
                        R.string.settings_homepage_open_last_tab,
                    ),
                    RadioButtonOptionWithDescription(
                        HomepageOpeningScreen.HOMEPAGE_AFTER_FOUR_HOURS,
                        R.string.settings_homepage_open_after_four_hours,
                    )
                )
            },
            value = appPrefs.homepageOpeningScreen,
            onValueChange = viewModel::updateHomepageOpeningScreen
        )
    }
}

@Composable
fun CustomizeSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_customize_title)) {
        PreferenceGroupLabel(label = R.string.appearance_label)
        PreferenceRadioSelectionPopup(
            label = R.string.appearance_label,
            options = remember {
                listOf(
                    RadioButtonOption(Appearance.LIGHT, R.string.available_appearance_light),
                    RadioButtonOption(Appearance.DARK, R.string.available_appearance_dark),
                    RadioButtonOption(Appearance.SYSTEM_SETTINGS, R.string.available_appearance_system)
                )
            },
            value = appPrefs.appearance,
            onValueChange = viewModel::updateAppearance
        )

        PreferenceGroupLabel(label = R.string.toolbar_position_label)
        PreferenceRadioSelectionPopup(
            label = R.string.toolbar_position_label,
            options = remember {
                listOf(
                    RadioButtonOption(ToolbarPosition.TOP, R.string.available_toolbar_position_top),
                    RadioButtonOption(ToolbarPosition.BOTTOM, R.string.available_toolbar_position_bottom)
                )
            },
            value = appPrefs.toolbarPosition,
            onValueChange = viewModel::updateToolbarPosition
        )
        TabsViewPreference(
            value = appPrefs.tabsView,
            onValueChange = viewModel::updateTabsView
        )

        PreferenceGroupLabel(label = R.string.settings_customize_gestures)
        PreferenceToggle(
            label = R.string.settings_pull_to_refresh,
            value = appPrefs.pullToRefreshEnabled,
            onValueChange = viewModel::updatePullToRefreshEnabled
        )
        PreferenceToggle(
            label = R.string.hide_toolbar_on_scroll_label,
            value = appPrefs.hideToolbarOnScroll,
            onValueChange = viewModel::updateHideToolbarOnScroll
        )
        PreferenceToggle(
            label = R.string.open_links_in_app_label,
            value = appPrefs.openLinksInApp,
            onValueChange = viewModel::updateOpenLinksInApp
        )
    }
}

@Composable
fun PasswordSettingsScreen(
    navigateTo: (NavDestination) -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_passwords_title)) {
        PreferenceGroupLabel(label = R.string.settings_passwords_title)
        PreferenceToggle(
            label = R.string.settings_save_passwords,
            description = R.string.settings_save_passwords_desc,
            value = appPrefs.savePasswordsEnabled,
            onValueChange = viewModel::updateSavePasswordsEnabled
        )
        PreferenceToggle(
            label = R.string.settings_password_autofill,
            description = R.string.settings_password_autofill_desc,
            value = appPrefs.passwordAutofillEnabled,
            onValueChange = viewModel::updatePasswordAutofillEnabled
        )
        PreferenceGroupLabel(label = R.string.settings_passwords_manage)
        SettingsNavRow(
            label = R.string.settings_saved_passwords,
            description = stringResource(R.string.settings_saved_passwords_secure_desc),
            onClicked = { navigateTo(NavDestination.SavedPasswords) },
        )
    }
}

@Composable
fun AutofillSettingsScreen(
    navigateTo: (NavDestination) -> Unit = {},
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_autofill_title)) {
        PreferenceGroupLabel(label = R.string.settings_autofill_addresses)
        PreferenceToggle(
            label = R.string.settings_autofill_save_addresses,
            description = R.string.settings_autofill_save_addresses_desc,
            value = appPrefs.autofillAddressesEnabled,
            onValueChange = viewModel::updateAutofillAddressesEnabled
        )
        SettingsNavRow(
            label = R.string.settings_autofill_add_address,
            description = stringResource(R.string.settings_autofill_manage_secure_desc),
            onClicked = { navigateTo(NavDestination.SavedAutofill) },
        )

        PreferenceGroupLabel(label = R.string.settings_autofill_cards)
        PreferenceToggle(
            label = R.string.settings_autofill_save_cards,
            description = R.string.settings_autofill_save_cards_desc,
            value = appPrefs.autofillCardsEnabled,
            onValueChange = viewModel::updateAutofillCardsEnabled
        )
        SettingsNavRow(
            label = R.string.settings_autofill_add_card,
            description = stringResource(R.string.settings_autofill_manage_secure_desc),
            onClicked = { navigateTo(NavDestination.SavedAutofill) },
        )
    }
}

@Composable
fun AccessibilitySettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_accessibility_title)) {
        PreferenceToggle(
            label = R.string.settings_accessibility_auto_font,
            description = R.string.settings_accessibility_auto_font_desc,
            value = appPrefs.accessibilityAutomaticFontSizing,
            onValueChange = viewModel::updateAccessibilityAutomaticFontSizing
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = stringResource(R.string.settings_accessibility_font_size))
            Slider(
                value = appPrefs.accessibilityFontScale.coerceIn(80, 150).toFloat(),
                onValueChange = { viewModel.updateAccessibilityFontScale(it.toInt()) },
                valueRange = 80f..150f,
                steps = 6,
                enabled = !appPrefs.accessibilityAutomaticFontSizing
            )
            Text(
                text = stringResource(R.string.settings_accessibility_font_percent, appPrefs.accessibilityFontScale),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                text = stringResource(
                    if (appPrefs.accessibilityAutomaticFontSizing) {
                        R.string.settings_accessibility_font_disabled_hint
                    } else {
                        R.string.settings_accessibility_font_reload_hint
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!appPrefs.accessibilityAutomaticFontSizing && appPrefs.accessibilityFontScale != 100) {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.updateAccessibilityFontScale(100) }
                ) {
                    Text(stringResource(R.string.settings_accessibility_font_reset))
                }
            }
        }
        PreferenceToggle(
            label = R.string.settings_accessibility_force_zoom,
            description = R.string.settings_accessibility_force_zoom_desc,
            value = appPrefs.accessibilityForceZoomEnabled,
            onValueChange = viewModel::updateAccessibilityForceZoomEnabled
        )
    }
}

@Composable
private fun PreferenceScreenScaffold(
    title: String,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(title = title, scrollableState = scrollState)
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(bottom = 16.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsNavRow(
    label: Int,
    description: String,
    onClicked: () -> Unit
) {
    PreferenceRow(
        label = label,
        description = description,
        trailing = {
            Icon(
                painter = painterResource(id = R.drawable.icons_arrow_forward),
                contentDescription = "Open"
            )
        },
        onClicked = onClicked
    )
}

@Composable
private fun openingScreenDescription(value: HomepageOpeningScreen): String {
    return stringResource(
        when (value) {
            HomepageOpeningScreen.HOMEPAGE -> R.string.settings_homepage_open_homepage
            HomepageOpeningScreen.LAST_TAB -> R.string.settings_homepage_open_last_tab
            HomepageOpeningScreen.HOMEPAGE_AFTER_FOUR_HOURS,
            HomepageOpeningScreen.UNRECOGNIZED -> R.string.settings_homepage_open_after_four_hours
        }
    )
}

@Composable
private fun passwordSummary(appPrefs: AppPreferences): String {
    return stringResource(
        if (appPrefs.passwordAutofillEnabled || appPrefs.savePasswordsEnabled) {
            R.string.settings_on
        } else {
            R.string.settings_off
        }
    )
}

@Composable
private fun autofillSummary(appPrefs: AppPreferences): String {
    return stringResource(
        if (appPrefs.autofillAddressesEnabled || appPrefs.autofillCardsEnabled) {
            R.string.settings_on
        } else {
            R.string.settings_off
        }
    )
}

@Composable
private fun accessibilitySummary(appPrefs: AppPreferences): String {
    return if (appPrefs.accessibilityAutomaticFontSizing) {
        stringResource(R.string.settings_accessibility_auto_font)
    } else {
        stringResource(R.string.settings_accessibility_font_percent, appPrefs.accessibilityFontScale)
    }
}
