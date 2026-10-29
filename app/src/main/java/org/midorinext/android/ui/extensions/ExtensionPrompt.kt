package org.midorinext.android.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import mozilla.components.browser.state.state.extension.WebExtensionPromptRequest
import mozilla.components.feature.addons.Addon
import org.midorinext.android.R

@Composable
fun ExtensionPrompt(viewModel: ExtensionPromptViewModel = hiltViewModel()) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val installing by viewModel.installing.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    when (val request = prompt) {
        is WebExtensionPromptRequest.InstallationRequested -> {
            AlertDialog(
                onDismissRequest = { viewModel.respond(request, false) },
                title = { Text(request.name ?: stringResource(R.string.extensions_install)) },
                text = {
                    Text(stringResource(R.string.extensions_website_install_prompt, request.url),
                        Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.install(request) }, enabled = !installing) {
                        Text(stringResource(R.string.extensions_install))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.respond(request, false) }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        is WebExtensionPromptRequest.AfterInstallation.Permissions -> {
            val context = LocalContext.current
            val permissions = remember(request, context) {
                val (permissions, origins, data) = when (request) {
                    is WebExtensionPromptRequest.AfterInstallation.Permissions.Required ->
                        Triple(request.permissions, request.origins, request.dataCollectionPermissions)
                    is WebExtensionPromptRequest.AfterInstallation.Permissions.Optional ->
                        Triple(request.permissions, request.origins, request.dataCollectionPermissions)
                }
                Addon.localizePermissions(permissions + origins, context) +
                    Addon.localizeDataCollectionPermissions(data, context)
            }
            AlertDialog(
                onDismissRequest = { viewModel.respond(request, false) },
                title = { Text(request.extension.getMetadata()?.name ?: request.extension.id) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.extensions_permission_prompt))
                        if (permissions.isEmpty()) Text(stringResource(R.string.extensions_no_permissions))
                        permissions.forEach { Text(it) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.respond(request, true) }) {
                        Text(stringResource(R.string.extensions_allow))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.respond(request, false) }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        else -> if (installing) {
            AlertDialog(
                onDismissRequest = viewModel::cancelInstallation,
                title = { Text(stringResource(R.string.extensions_installing)) },
                text = { CircularProgressIndicator() },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = viewModel::cancelInstallation) { Text(stringResource(R.string.cancel)) }
                },
            )
        } else if (failed) {
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                title = { Text(stringResource(R.string.extensions_install_error)) },
                text = { Text(stringResource(R.string.extensions_error_action_actionable)) },
                confirmButton = {
                    TextButton(onClick = viewModel::clearError) { Text(stringResource(R.string.ok)) }
                },
            )
        }
    }
}
