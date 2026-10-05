package org.midorinext.android.pwa

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun WebAppsScreen(viewModel: WebAppsViewModel = hiltViewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var appToRemove by remember { mutableStateOf<InstalledWebApp?>(null) }
    var appToClear by remember { mutableStateOf<InstalledWebApp?>(null) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader(title = stringResource(R.string.pwa_manage_title))
            LazyColumn {
                item {
                    Text(
                        text = stringResource(R.string.pwa_offline_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (apps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.pwa_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(apps, key = { it.startUrl }) { app ->
                    val enableDescription = stringResource(R.string.pwa_enable_app, app.name)
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    app.startUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = app.enabled,
                                onCheckedChange = { viewModel.setEnabled(app, it) },
                                modifier = Modifier.padding(start = 12.dp).semantics {
                                    contentDescription = enableDescription
                                },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    context.startActivity(Intent(context, WebAppActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        data = Uri.parse(app.startUrl)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                                    })
                                },
                                enabled = app.enabled,
                            ) { Text(stringResource(R.string.pwa_open)) }
                            TextButton(onClick = { appToRemove = app }) {
                                Text(stringResource(R.string.pwa_remove))
                            }
                            TextButton(onClick = { appToClear = app }) {
                                Text(stringResource(R.string.pwa_clear_data))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    appToRemove?.let { app ->
        AlertDialog(
            onDismissRequest = { appToRemove = null },
            title = { Text(stringResource(R.string.pwa_remove_title, app.name)) },
            text = { Text(stringResource(R.string.pwa_remove_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.uninstall(app)
                    appToRemove = null
                }) { Text(stringResource(R.string.pwa_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { appToRemove = null }) { Text(stringResource(R.string.pwa_cancel)) }
            },
        )
    }

    appToClear?.let { app ->
        val failedMessage = stringResource(R.string.pwa_clear_data_failed)
        val doneMessage = stringResource(R.string.pwa_clear_data_done)
        AlertDialog(
            onDismissRequest = { appToClear = null },
            title = { Text(stringResource(R.string.pwa_clear_data_title, app.name)) },
            text = { Text(stringResource(R.string.pwa_clear_data_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSiteData(app) { success ->
                        Toast.makeText(
                            context,
                            if (success) doneMessage else failedMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    appToClear = null
                }) { Text(stringResource(R.string.pwa_clear_data)) }
            },
            dismissButton = {
                TextButton(onClick = { appToClear = null }) { Text(stringResource(R.string.pwa_cancel)) }
            },
        )
    }
}
