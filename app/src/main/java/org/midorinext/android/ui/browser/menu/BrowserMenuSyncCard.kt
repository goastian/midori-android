package org.midorinext.android.ui.browser.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import org.midorinext.android.R
import org.midorinext.android.sync.SyncUiState
import org.midorinext.android.sync.SyncWorkerEntryPoint
import org.midorinext.android.sync.syncError

@Composable
internal fun BrowserMenuSyncCard(onClick: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val coordinator = remember(context) {
        EntryPointAccessors.fromApplication(context, SyncWorkerEntryPoint::class.java).syncCoordinator()
    }
    val state by coordinator.ui.collectAsStateWithLifecycle()
    BrowserMenuAccountCard(state, onClick)
}

@Composable
internal fun BrowserMenuAccountCard(state: SyncUiState, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("browser_menu_sync"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (state.connected && state.name.isNotBlank()) {
                    Text(state.name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(painterResource(R.drawable.icons_account), contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (state.connected) state.name.ifBlank { stringResource(R.string.sync_title) }
                    else stringResource(R.string.menu_login),
                modifier = Modifier.testTag("browser_menu_sync_name"),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = when {
                    state.error != null -> stringResource(syncError(state.error))
                    !state.connected -> stringResource(R.string.sync_sign_in_authentik)
                    state.phase == "keys_required" -> stringResource(R.string.sync_create_keys)
                    state.phase == "recovery_required" -> stringResource(R.string.sync_unlock)
                    state.phase == "backup_required" -> stringResource(R.string.sync_backup_summary)
                    state.phase != "ready" -> stringResource(R.string.sync_account_connecting)
                    state.conflicts > 0 -> stringResource(R.string.sync_conflicts, state.conflicts)
                    state.busy -> stringResource(R.string.sync_account_connecting)
                    state.pending > 0 -> stringResource(R.string.sync_pending_count, state.pending)
                    state.lastSync == 0L -> stringResource(R.string.sync_pending)
                    else -> stringResource(R.string.sync_on)
                },
                modifier = Modifier.testTag("browser_menu_sync_status"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(painterResource(R.drawable.icons_chevron_forward), contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
    }
}
