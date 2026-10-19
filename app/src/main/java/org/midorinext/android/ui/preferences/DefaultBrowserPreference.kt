package org.midorinext.android.ui.preferences

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.R
import org.midorinext.android.ext.defaultBrowserStatus
import org.midorinext.android.ui.preferences.widgets.PreferenceRow
import org.midorinext.android.ui.preferences.widgets.PreferenceToggle

@Composable
fun DefaultBrowserPreference() {
    val context = LocalContext.current
    var status by remember(context) { mutableStateOf(context.defaultBrowserStatus()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        status = context.defaultBrowserStatus()
    }

    if (status.roleRequestIntent != null) {
        PreferenceToggle(
            label = R.string.default_browser_label,
            value = status.isDefault,
            onValueChange = {
                if (it) {
                    launcher.launch(status.requestIntent())
                } else {
                    launcher.launch(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                }
            },
        )
    } else {
        PreferenceRow(
            label = R.string.default_browser_label,
            onClicked = { launcher.launch(status.requestIntent()) },
        )
    }
}
