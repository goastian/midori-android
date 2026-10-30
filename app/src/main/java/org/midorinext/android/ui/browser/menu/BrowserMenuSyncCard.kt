package org.midorinext.android.ui.browser.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import org.midorinext.android.R
import org.midorinext.android.sync.SyncUiState
import org.midorinext.android.sync.SyncWorkerEntryPoint

@Composable
internal fun BrowserMenuSyncCard(onClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) {
        EntryPointAccessors.fromApplication(context, SyncWorkerEntryPoint::class.java).syncCoordinator()
    }
    val state by coordinator.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var disconnect by remember { mutableStateOf(false) }
    BrowserMenuAccountCard(state, onClick, onPower = { if (state.connected) disconnect = true else onClick() })
    if (disconnect) AlertDialog(
        onDismissRequest = { disconnect = false },
        title = { Text(stringResource(R.string.sync_disconnect)) },
        text = { Text(stringResource(R.string.sync_disconnect_summary)) },
        confirmButton = {
            TextButton(onClick = {
                disconnect = false
                scope.launch { coordinator.disconnect() }
            }) { Text(stringResource(R.string.sync_disconnect)) }
        },
        dismissButton = {
            TextButton(onClick = { disconnect = false }) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
internal fun BrowserMenuAccountCard(state: SyncUiState, onClick: () -> Unit, onPower: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .testTag("browser_menu_sync"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(56.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.connected && state.name.isNotBlank()) Text(state.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
                    else Icon(painterResource(R.drawable.icons_account), contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (state.connected) state.name.ifBlank { stringResource(R.string.sync_title) } else stringResource(R.string.sync_guest),
                        modifier = Modifier.weight(1f).testTag("browser_menu_sync_name"), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onPower, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("browser_menu_sync_power")) {
                        Icon(painterResource(R.drawable.icons_power),
                            contentDescription = stringResource(if (state.connected) R.string.sync_disconnect else R.string.sync_sign_in_authentik),
                            tint = if (state.connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("browser_menu_sync_settings")) {
                        Icon(painterResource(R.drawable.icons_settings), contentDescription = stringResource(R.string.sync_account_settings))
                    }
                }
                Text(if (state.connected) state.email else stringResource(R.string.sync_sign_in_authentik),
                    style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Surface(shape = CircleShape,
                    color = if (state.connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = if (state.connected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp).testTag("browser_menu_sync_status")) {
                    Text(stringResource(if (state.connected) R.string.sync_on else R.string.sync_off),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                if (state.connected && state.phase != "ready") Text(stringResource(R.string.sync_account_connecting),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
