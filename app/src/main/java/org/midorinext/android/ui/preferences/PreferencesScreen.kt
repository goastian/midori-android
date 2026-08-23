package org.midorinext.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import android.content.Intent
import android.os.Build
import android.provider.Settings
import mozilla.components.concept.engine.translate.Language
import mozilla.components.concept.engine.translate.LanguageModel
import mozilla.components.concept.engine.translate.LanguageSetting
import mozilla.components.concept.engine.translate.ModelState

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
                description = if (BuildConfig.FLAVOR_version == "original") {
                    stringResource(R.string.settings_homepage_summary)
                } else {
                    openingScreenDescription(appPrefs.homepageOpeningScreen)
                },
                onClicked = { navigateTo(NavDestination.HomepageSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_customize_title,
                description = stringResource(R.string.settings_customize_summary),
                onClicked = { navigateTo(NavDestination.CustomizeSettings) }
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SettingsNavRow(
                    label = R.string.settings_app_language,
                    description = stringResource(R.string.settings_app_language_summary),
                    onClicked = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                data = android.net.Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                )
            }
            SettingsNavRow(
                label = R.string.settings_translations_title,
                description = stringResource(R.string.settings_translations_summary),
                onClicked = { navigateTo(NavDestination.TranslationSettings) }
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
            SettingsNavRow(
                label = R.string.settings_notifications_title,
                description = stringResource(R.string.settings_notifications_summary),
                onClicked = { navigateTo(NavDestination.NotificationSettings) }
            )
            SettingsNavRow(
                label = R.string.settings_downloads_title,
                description = stringResource(R.string.settings_downloads_summary),
                onClicked = { navigateTo(NavDestination.DownloadSettings) }
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
        if (BuildConfig.FLAVOR_version == "original") {
            PreferenceRow(
                label = R.string.settings_homepage_midori_tab,
                description = stringResource(R.string.settings_homepage_midori_tab_description),
            )
        } else {
            PreferenceToggle(
                label = R.string.show_new_tab_home_label,
                description = R.string.show_new_tab_home_description,
                value = !appPrefs.openBlankNewTab,
                onValueChange = viewModel::updateShowNewTabHome
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

        PreferenceGroupLabel(label = R.string.settings_customize_shortcut)
        PreferenceRadioSelectionPopup(
            label = R.string.settings_toolbar_shortcut,
            options = remember {
                listOf(
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_NEW_TAB, R.string.shortcut_new_tab),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_SHARE, R.string.shortcut_share),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_BOOKMARK, R.string.shortcut_bookmark),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_HOMEPAGE, R.string.shortcut_homepage),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_BACK, R.string.shortcut_back),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_SUMMARIZE, R.string.shortcut_summarize),
                    RadioButtonOption(ToolbarShortcut.TOOLBAR_SHORTCUT_NONE, R.string.shortcut_none)
                )
            },
            value = appPrefs.toolbarShortcut,
            onValueChange = viewModel::updateToolbarShortcut
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
            label = R.string.settings_swipe_address_bar_to_switch_tabs,
            value = appPrefs.swipeAddressBarToSwitchTabsEnabled,
            onValueChange = viewModel::updateSwipeAddressBarToSwitchTabsEnabled
        )
        PreferenceToggle(
            label = R.string.settings_swipe_toolbar_to_show_tabs,
            value = appPrefs.swipeToolbarToShowTabsEnabled,
            onValueChange = viewModel::updateSwipeToolbarToShowTabsEnabled
        )
        PreferenceToggle(
            label = R.string.settings_shake_to_summarize,
            description = R.string.settings_shake_to_summarize_summary,
            value = appPrefs.shakeToSummarizeEnabled,
            onValueChange = viewModel::updateShakeToSummarizeEnabled
        )
    }
}

@Composable
fun NotificationSettingsScreen() {
    val context = LocalContext.current
    PreferenceScreenScaffold(title = stringResource(R.string.settings_notifications_title)) {
        PreferenceGroupLabel(label = R.string.settings_notifications_title)
        PreferenceRow(
            label = R.string.settings_notifications_open_system,
            description = stringResource(R.string.settings_notifications_open_system_summary),
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.icons_open),
                    contentDescription = null
                )
            },
            onClicked = {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                    }
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun DownloadSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_downloads_title)) {
        PreferenceGroupLabel(label = R.string.download_settings_file_storage)
        PreferenceRow(
            label = R.string.download_settings_default_location,
            description = stringResource(R.string.download_settings_default_location_summary)
        )
        PreferenceToggle(
            label = R.string.download_wifi_only,
            description = R.string.download_wifi_only_summary,
            value = appPrefs.downloadWifiOnly,
            onValueChange = viewModel::updateDownloadWifiOnly
        )

        PreferenceGroupLabel(label = R.string.download_settings_delete_or_remove)
        PreferenceRadioSelectionPopup(
            label = R.string.download_settings_delete_or_remove,
            options = remember {
                listOf(
                    RadioButtonOption(
                        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_DELETE_FROM_DEVICE,
                        R.string.download_removal_delete_from_device
                    ),
                    RadioButtonOption(
                        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_REMOVE_FROM_HISTORY,
                        R.string.download_removal_remove_from_history
                    ),
                    RadioButtonOption(
                        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_ASK,
                        R.string.download_removal_ask
                    )
                )
            },
            value = appPrefs.downloadRemovalBehavior,
            onValueChange = viewModel::updateDownloadRemovalBehavior
        )
        PreferenceToggle(
            label = R.string.download_manage_with_other_app,
            description = R.string.download_manage_with_other_app_summary,
            value = appPrefs.manageDownloadsWithOtherApp,
            onValueChange = viewModel::updateManageDownloadsWithOtherApp
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
fun TranslationSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val appPrefs by viewModel.appPreferences.collectAsState()
    val translationState by viewModel.translationState.collectAsState()
    var showAutomaticLanguages by remember { mutableStateOf(false) }
    var showNeverTranslateSites by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }

    val supportedLanguages = translationState.supportedLanguages?.fromLanguages.orEmpty()
        .distinctBy { it.code }
        .sortedBy { it.localizedDisplayName ?: it.code }
    val automaticLanguages = translationState.languageSettings.orEmpty()
        .filterValues { it == LanguageSetting.ALWAYS }
        .keys
    val automaticSummary = supportedLanguages
        .filter { it.code in automaticLanguages }
        .joinToString { it.localizedDisplayName ?: it.code }
        .ifBlank { stringResource(R.string.settings_translations_automatic_none) }
    val neverTranslateSites = translationState.neverTranslateSites.orEmpty()
    val downloadedLanguages = translationState.languageModels.orEmpty()
        .count { it.status == ModelState.DOWNLOADED }

    PreferenceScreenScaffold(title = stringResource(R.string.settings_translations_title)) {
        PreferenceToggle(
            label = R.string.settings_translations_enabled,
            description = R.string.settings_translations_enabled_summary,
            value = !appPrefs.translationsDisabled,
            onValueChange = viewModel::updateTranslationsEnabled
        )

        if (!appPrefs.translationsDisabled) {
            PreferenceToggle(
                label = R.string.settings_translations_offer,
                value = translationState.offerTranslation ?: true,
                onValueChange = viewModel::updateTranslationOffer
            )
            PreferenceToggle(
                label = R.string.settings_translations_data_saver,
                value = appPrefs.translationsDownloadInDataSaver,
                onValueChange = viewModel::updateTranslationsDownloadInDataSaver
            )

            PreferenceGroupLabel(label = R.string.settings_translations_preferences)
            SettingsNavRow(
                label = R.string.settings_translations_automatic,
                description = automaticSummary,
                onClicked = { showAutomaticLanguages = true }
            )
            SettingsNavRow(
                label = R.string.settings_translations_never_sites,
                description = if (neverTranslateSites.isEmpty()) {
                    stringResource(R.string.settings_translations_never_sites_none)
                } else {
                    stringResource(
                        R.string.settings_translations_never_sites_count,
                        neverTranslateSites.size
                    )
                },
                onClicked = { showNeverTranslateSites = true }
            )
            SettingsNavRow(
                label = R.string.settings_translations_download_languages,
                description = stringResource(
                    R.string.settings_translations_downloaded_count,
                    downloadedLanguages
                ),
                onClicked = { showDownloads = true }
            )
        }
    }

    if (showAutomaticLanguages) {
        AutomaticTranslationDialog(
            languages = supportedLanguages,
            automaticLanguages = automaticLanguages,
            onLanguageChecked = viewModel::updateAutomaticTranslation,
            onDismiss = { showAutomaticLanguages = false }
        )
    }
    if (showNeverTranslateSites) {
        NeverTranslateSitesDialog(
            sites = neverTranslateSites,
            onRemove = viewModel::removeNeverTranslateSite,
            onDismiss = { showNeverTranslateSites = false }
        )
    }
    if (showDownloads) {
        TranslationDownloadsDialog(
            models = translationState.languageModels.orEmpty(),
            onModelChecked = viewModel::manageLanguageModel,
            onDismiss = { showDownloads = false }
        )
    }
}

@Composable
private fun AutomaticTranslationDialog(
    languages: List<Language>,
    automaticLanguages: Set<String>,
    onLanguageChecked: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_translations_manage_automatic)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_translations_manage_automatic_summary))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    languages.forEach { language ->
                        val checked = language.code in automaticLanguages
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onLanguageChecked(language.code, it) }
                            )
                            Text(language.localizedDisplayName ?: language.code)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_translations_done))
            }
        }
    )
}

@Composable
private fun NeverTranslateSitesDialog(
    sites: List<String>,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_translations_manage_sites)) },
        text = {
            if (sites.isEmpty()) {
                Text(stringResource(R.string.settings_translations_manage_sites_empty))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    sites.forEach { site ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = site, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onRemove(site) }) {
                                Text(stringResource(R.string.settings_translations_remove))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_translations_done))
            }
        }
    )
}

@Composable
private fun TranslationDownloadsDialog(
    models: List<LanguageModel>,
    onModelChecked: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_translations_manage_downloads)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_translations_manage_downloads_summary))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    models.filter { it.language != null }
                        .sortedBy { it.language?.localizedDisplayName ?: it.language?.code }
                        .forEach { model ->
                            val language = requireNotNull(model.language)
                            val downloading = model.status == ModelState.DOWNLOAD_IN_PROGRESS
                            val downloaded = model.status == ModelState.DOWNLOADED
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = downloaded || downloading,
                                    enabled = !downloading,
                                    onCheckedChange = { onModelChecked(language.code, it) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(language.localizedDisplayName ?: language.code)
                                    if (downloading || downloaded) {
                                        Text(
                                            text = stringResource(
                                                if (downloading) {
                                                    R.string.settings_translations_model_downloading
                                                } else {
                                                    R.string.settings_translations_model_downloaded
                                                }
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_translations_done))
            }
        }
    )
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
