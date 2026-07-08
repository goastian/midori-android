package org.midorinext.android.ui.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.midorinext.android.ui.preferences.permissions.PermissionsPreference
import org.midorinext.android.ui.preferences.widgets.*
import org.midorinext.android.ui.widgets.HtmlText
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun PreferencesScreen(
    onClose: () -> Unit,
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

            // Toolbar position
            PreferenceRadioSelectionPopup(
                label = R.string.toolbar_position_label,
                options = remember { listOf(
                    RadioButtonOption(ToolbarPosition.TOP, R.string.available_toolbar_position_top),
                    RadioButtonOption(ToolbarPosition.BOTTOM, R.string.available_toolbar_position_bottom)
                ) },
                value = appPrefs.toolbarPosition,
                onValueChange = { viewModel.updateToolbarPosition(it) }
            )
            // Hide toolbar on scroll
            /* PreferenceToggle(
                label = R.string.hide_toolbar_on_scroll_label,
                value = appPrefs.hideToolbarOnScroll,
                onValueChange = { viewModel.updateHideToolbarOnScroll(it) }
            ) */
            // Tabs view
            TabsViewPreference(
                value = appPrefs.tabsView,
                onValueChange = { viewModel.updateTabsView(it) }
            )
            // Appearance // TODO move appearance to app preferences
            PreferenceRadioSelectionPopup(
                label = R.string.appearance_label,
                options =  remember { listOf(
                    RadioButtonOption(Appearance.LIGHT, R.string.available_appearance_light),
                    RadioButtonOption(Appearance.DARK, R.string.available_appearance_dark),
                    RadioButtonOption(Appearance.SYSTEM_SETTINGS, R.string.available_appearance_system)
                ) },
                value = appPrefs.appearance,
                onValueChange = { viewModel.updateAppearance(it) }
            )
            // Open links in app
            PreferenceToggle(
                label = R.string.open_links_in_app_label,
                value = appPrefs.openLinksInApp,
                onValueChange = { viewModel.updateOpenLinksInApp(it) }
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
