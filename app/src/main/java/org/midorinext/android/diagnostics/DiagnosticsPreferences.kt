package org.midorinext.android.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.midorinext.android.R
import org.midorinext.android.ui.preferences.widgets.PreferenceRow

@Composable
fun DiagnosticsPrompt(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    if (!consent.answered) DiagnosticsConsentDialog(consent.enabled, viewModel::setEnabled)
}

@Composable
internal fun DiagnosticsConsentDialog(initiallyEnabled: Boolean = true, onSave: (Boolean) -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(initiallyEnabled) }
    val label = stringResource(R.string.stability_enabled)
    val title = stringResource(R.string.stability_prompt_title)
    val description = stringResource(R.string.stability_description)
    val save = stringResource(R.string.stability_save)
    val decline = stringResource(R.string.stability_decline)
    AlertDialog(
        modifier = Modifier.testTag("diagnostics_consent"),
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(description)
                Text(stringResource(R.string.stability_coverage))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, Modifier.weight(1f))
                    Switch(enabled, onCheckedChange = { enabled = it },
                        modifier = Modifier.testTag("diagnostics_consent_switch").semantics { contentDescription = label })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(enabled) }, modifier = Modifier.testTag("diagnostics_save")) {
                Text(save)
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(false) }, modifier = Modifier.testTag("diagnostics_decline")) {
                Text(decline)
            }
        },
    )
}

@Composable
fun DiagnosticsPreference(viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val consent by viewModel.consent.collectAsStateWithLifecycle()
    val label = stringResource(R.string.stability_enabled)
    PreferenceRow(
        label = R.string.stability_enabled,
        description = stringResource(R.string.stability_coverage),
        modifier = Modifier.testTag("diagnostics_preference"),
        onClicked = { viewModel.setEnabled(!consent.enabled) },
        trailing = {
            Switch(consent.enabled, onCheckedChange = viewModel::setEnabled,
                modifier = Modifier.testTag("diagnostics_preference_switch").semantics { contentDescription = label })
        },
    )
}
