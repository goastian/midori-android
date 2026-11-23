package org.midorinext.android.ui.preferences.permissions

import androidx.compose.runtime.Composable
import org.midorinext.android.R
import org.midorinext.android.ui.preferences.PreferencesViewModel
import org.midorinext.android.ui.preferences.widgets.PreferenceSelectionPopup
import mozilla.components.ui.icons.R as iconsR

@Composable
fun PermissionsPreference(
    viewModel: PreferencesViewModel
) {
    PreferenceSelectionPopup(
        label = R.string.permissions_settings_label,
        leadingIcon = iconsR.drawable.mozac_ic_permissions_24,
        popupContent = {
            PermissionsSettingsList(viewModel)
        },
        fullscreenPopup = true,
        disableScreenHeader = true
    )
}
