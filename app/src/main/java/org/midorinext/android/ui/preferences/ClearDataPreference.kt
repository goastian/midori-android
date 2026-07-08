package org.midorinext.android.ui.preferences

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.preferences.widgets.PreferenceSelectionPopup
import org.midorinext.android.ui.widgets.BigButton
import mozilla.components.concept.engine.Engine

private data class ClearBrowsingDataOption(
    @StringRes val label: Int,
    var checked: Boolean,
    val value: Int
)

@Composable
fun ClearDataPreference(
    viewModel: PreferencesViewModel,
    applicationViewModel: MidoriApplicationViewModel
) {
    val prefs by viewModel.clearDataPreferences.collectAsState()

    val browsingDataOptions = remember(prefs.browsingData) {
        val allChecked = prefs.browsingData.contains(Engine.BrowsingData.ALL)
        listOf(
            ClearBrowsingDataOption(
                label = R.string.cleardata_cache,
                checked = allChecked || prefs.browsingData.contains(Engine.BrowsingData.allCaches().types),
                value = Engine.BrowsingData.allCaches().types
            ),
            ClearBrowsingDataOption(R.string.cleardata_cookies, allChecked || prefs.browsingData.contains(Engine.BrowsingData.COOKIES), Engine.BrowsingData.COOKIES),
            ClearBrowsingDataOption(R.string.cleardata_sessions, allChecked || prefs.browsingData.contains(Engine.BrowsingData.AUTH_SESSIONS), Engine.BrowsingData.AUTH_SESSIONS),
            ClearBrowsingDataOption(R.string.cleardata_storage, allChecked || prefs.browsingData.contains(Engine.BrowsingData.DOM_STORAGES), Engine.BrowsingData.DOM_STORAGES),
            ClearBrowsingDataOption(R.string.cleardata_permissions, allChecked || prefs.browsingData.contains(Engine.BrowsingData.PERMISSIONS), Engine.BrowsingData.PERMISSIONS)
        )
    }

    PreferenceSelectionPopup(
        label = R.string.cleardata_settings_label,
        popupContent = {
            Column(modifier = Modifier.fillMaxSize()) {
                CheckBoxRow(
                    label = R.string.cleardata_allsites,
                    checked = prefs.browsingData.contains(Engine.BrowsingData.ALL)
                            || browsingDataOptions.all { it.checked },
                    onCheckedChange = { checked ->
                        viewModel.updateClearDataPreferences(
                            prefs.copy(browsingData =
                                if (checked) Engine.BrowsingData.all()
                                else Engine.BrowsingData.none()
                            )
                        )
                    }
                )
                Column(modifier = Modifier.padding(start = 20.dp)) {
                    browsingDataOptions.forEach { option ->
                        CheckBoxRow(
                            label = option.label,
                            checked = option.checked,
                            onCheckedChange = { checked ->
                                option.checked = checked
                                viewModel.updateClearDataPreferences(
                                    prefs.copy(browsingData =
                                        Engine.BrowsingData.select(
                                            *browsingDataOptions
                                                .filter { it.checked }
                                                .map { it.value }
                                                .toIntArray()
                                        )
                                    )
                                )
                            }
                        )
                    }
                }
                CheckBoxRow(
                    label = R.string.history,
                    checked = prefs.history,
                    onCheckedChange = {
                        viewModel.updateClearDataPreferences(prefs.copy(history = it))
                    }
                )
                CheckBoxRow(
                    label = R.string.cleardata_tabs,
                    checked = prefs.tabs,
                    onCheckedChange = {
                        viewModel.updateClearDataPreferences(prefs.copy(tabs = it))
                    }
                )
                CheckBoxRow(
                    label = R.string.cleardata_tabs_private,
                    checked = true,
                    enabled = false
                )

                var zapEnabled by remember { mutableStateOf(true) }
                val clearDataDoneString = stringResource(id = R.string.cleardata_done)
                BigButton(
                    text = R.string.cleardata_use_now,
                    icon = R.drawable.icons_zap_night,
                    enabled = zapEnabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp),
                    onClick = { applicationViewModel.zap(from = "Settings") { success ->
                        if (success) {
                            applicationViewModel.showSnackbar(clearDataDoneString)
                            zapEnabled = false
                        } else {
                            // TODO handle zap failed
                        }
                    } }
                )
            }
        },
        fullscreenPopup = true
    )
}

@Composable
fun CheckBoxRow(
    @StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit = {},
    enabled: Boolean = true
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(text = stringResource(id = label), fontSize = 16.sp)
    }
}

fun Engine.BrowsingData.Companion.none() = select(0)