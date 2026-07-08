package org.midorinext.android.ui.preferences.permissions

import androidx.compose.runtime.Composable
import org.midorinext.android.R
import org.midorinext.android.ui.preferences.PreferencesViewModel
import org.midorinext.android.ui.preferences.widgets.PreferenceSelectionPopup

@Composable
fun PermissionsPreference(
    viewModel: PreferencesViewModel
) {
    PreferenceSelectionPopup(
        label = R.string.permissions_settings_label,
        popupContent = {
            PermissionsSettingsList(viewModel)
        },
        fullscreenPopup = true,
        disableScreenHeader = true
    )
}