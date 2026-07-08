package org.midorinext.android.ui.preferences

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.R
import org.midorinext.android.ext.openDefaultAppsSystemSettings
import org.midorinext.android.ui.preferences.widgets.PreferenceRow
import org.midorinext.android.ui.preferences.widgets.PreferenceToggle

@RequiresApi(Build.VERSION_CODES.Q)
private class RoleBrowserData(
    context: Context
) {
    val manager: RoleManager = context.getSystemService(android.app.role.RoleManager::class.java)
    val isRoleAvailable: Boolean = manager.isRoleAvailable(RoleManager.ROLE_BROWSER)
    val isRoleHeld: Boolean = isRoleAvailable && manager.isRoleHeld(RoleManager.ROLE_BROWSER)
    val requestIntent = manager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
}

@Composable
fun DefaultBrowserPreference() {
    val context = LocalContext.current

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleBrowserData = remember(context) { RoleBrowserData(context) }
        var value by remember(roleBrowserData) { mutableStateOf(roleBrowserData.isRoleHeld) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            value = (it.resultCode == Activity.RESULT_OK)
        }

        if (roleBrowserData.isRoleAvailable) {
            PreferenceToggle(
                label = R.string.default_browser_label,
                value = value,
                onValueChange = {
                    value = it
                    if (it) {
                        launcher.launch(roleBrowserData.requestIntent)
                    } else {
                        context.openDefaultAppsSystemSettings()
                    }
                }
            )
            return
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        PreferenceRow(
            label = R.string.default_browser_label,
            onClicked = { context.openDefaultAppsSystemSettings() }
        )
    }
    // Else no default browser functionality on android before. So don't show nothing
}