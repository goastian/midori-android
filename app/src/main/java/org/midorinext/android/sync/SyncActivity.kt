package org.midorinext.android.sync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.android.AndroidEntryPoint
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.midorinext.android.R
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import javax.inject.Inject

@AndroidEntryPoint
class SyncActivity : ComponentActivity() {
    @Inject internal lateinit var coordinator: SyncCoordinator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SyncScheduler.start(applicationContext, coordinator)
        handle(intent)
        setContent { MidoriBrowserTheme { SyncScreen(coordinator, onBack = { finish() }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handle(intent) }
    private fun handle(intent: Intent) {
        lifecycleScope.launch {
            runCatching {
                intent.data?.let { coordinator.completeLogin(it) } ?: coordinator.inspect()
                coordinator.sync()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SyncScreen(coordinator: SyncCoordinator, onBack: () -> Unit) {
    val state by coordinator.ui.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var actionError by remember { mutableStateOf<Int?>(null) }
    var conflicts by remember { mutableStateOf(emptyList<SyncConflict>()) }
    var disconnect by remember { mutableStateOf(false) }
    var recoveryCode by remember(state.accountId) { mutableStateOf("") }
    var backupCode by remember(state.accountId) { mutableStateOf("") }
    var backupSaved by remember(state.accountId) { mutableStateOf(false) }
    var loginUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingQr by remember { mutableStateOf<String?>(null) }
    fun run(block: suspend () -> Unit) { scope.launch { runCatching { block() } } }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            runCatching { SyncPairing.parse(contents) }
                .onSuccess { pendingQr = contents }
                .onFailure { actionError = R.string.sync_invalid_qr }
        }
    }
    fun scan() {
        actionError = null
        scanner.launch(ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setCaptureActivity(SyncQrActivity::class.java)
            .setPrompt(context.getString(R.string.sync_scan_prompt))
            .setBeepEnabled(false).setOrientationLocked(false))
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scan() else actionError = R.string.sync_camera_denied
    }
    Scaffold(topBar = { SyncAppBar(onBack) }) { padding ->
        if (!state.connected) {
            SyncWelcome(
                busy = state.busy,
                error = actionError?.let { stringResource(it) } ?: state.error?.let { stringResource(syncError(it)) },
                onScan = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scan()
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                },
                onSignIn = {
                    actionError = null
                    run {
                        loginUrl = coordinator.beginLogin(SyncTransport.DEFAULT_SERVER)
                    }
                },
                loginCode = state.loginCode,
                onCancelLogin = { loginUrl = null; run { coordinator.cancelLogin() } },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            actionError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            state.error?.let { Text(stringResource(syncError(it)), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.sync_summary))
            Text(state.name, style = MaterialTheme.typography.titleMedium)
            if (state.phase in listOf("keys_required", "backup_required")) {
                if (backupCode.isEmpty()) {
                    Button(onClick = { run { backupCode = coordinator.prepareKeys(); backupSaved = false } }, enabled = !state.busy) {
                        Text(stringResource(R.string.sync_create_keys))
                    }
                } else {
                    Text(stringResource(R.string.sync_backup_summary))
                    androidx.compose.foundation.text.selection.SelectionContainer { Text(backupCode) }
                    Row {
                        Checkbox(checked = backupSaved, onCheckedChange = { backupSaved = it }, enabled = !state.busy)
                        Text(stringResource(R.string.sync_backup_saved), Modifier.padding(top = 12.dp))
                    }
                    Button(onClick = { run { coordinator.activateKeys(); backupCode = ""; coordinator.sync() } }, enabled = backupSaved && !state.busy) {
                        Text(stringResource(R.string.sync_continue))
                    }
                }
            } else if (state.phase != "ready") {
                Text(stringResource(R.string.sync_unlock_summary))
                OutlinedTextField(value = recoveryCode, onValueChange = { recoveryCode = it }, singleLine = true,
                    label = { Text(stringResource(R.string.sync_recovery_code)) }, enabled = !state.busy,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Button(onClick = { run { coordinator.recover(recoveryCode); recoveryCode = ""; coordinator.sync() } }, enabled = recoveryCode.isNotBlank() && !state.busy) {
                    Text(stringResource(R.string.sync_unlock))
                }
                TextButton(onClick = { run { coordinator.sync() } }, enabled = !state.busy) {
                    Text(stringResource(R.string.sync_retry))
                }
            }
            if (state.phase == "ready") {
                for (name in SyncModels.schemas.keys) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(collectionLabel(name)), Modifier.weight(1f).padding(top = 12.dp))
                        Switch(name in state.selected, onCheckedChange = { enabled -> run { coordinator.select(name, enabled); coordinator.sync() } }, enabled = !state.busy)
                    }
                    if (name in state.unsupported) Text(stringResource(R.string.sync_unsupported), color = MaterialTheme.colorScheme.error)
                }
                Text(if (state.lastSync == 0L) stringResource(R.string.sync_pending) else stringResource(R.string.sync_last_sync,
                    java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastSync))))
                if (state.pending > 0) Text(stringResource(R.string.sync_pending_count, state.pending))
                Button(onClick = { run { coordinator.sync() } }, enabled = !state.busy) { Text(stringResource(R.string.sync_now)) }
            }
            if (state.conflicts > 0) OutlinedButton(onClick = { run { conflicts = coordinator.conflictSummaries() } }, enabled = !state.busy) { Text(stringResource(R.string.sync_conflicts, state.conflicts)) }
            for (conflict in conflicts) {
                val name = conflict.collection
                val id = conflict.id
                Text(stringResource(collectionLabel(name)))
                Text(stringResource(if (conflict.canResolve) R.string.sync_conflict_summary else R.string.sync_error_retry))
                if (conflict.canResolve) {
                    Text(stringResource(R.string.sync_local_copy, conflict.local ?: stringResource(R.string.sync_deleted_item)))
                    Text(stringResource(R.string.sync_remote_copy, conflict.remote ?: stringResource(R.string.sync_deleted_item)))
                }
                if (conflict.canResolve) Row {
                    TextButton(onClick = { run { coordinator.resolve(name, id, true); conflicts = coordinator.conflictSummaries(); coordinator.sync() } }, enabled = !state.busy) { Text(stringResource(R.string.sync_keep_local)) }
                    TextButton(onClick = { run { coordinator.resolve(name, id, false); conflicts = coordinator.conflictSummaries(); coordinator.sync() } }, enabled = !state.busy) { Text(stringResource(R.string.sync_use_remote)) }
                }
            }
            TextButton(onClick = { disconnect = true }, enabled = !state.busy) { Text(stringResource(R.string.sync_disconnect)) }
        }
    }
    if (loginUrl != null && state.loginCode.isNotEmpty()) AlertDialog(
        onDismissRequest = { loginUrl = null; run { coordinator.cancelLogin() } },
        title = { Text(stringResource(R.string.sync_login_code_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sync_login_code_summary))
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(state.loginCode, style = MaterialTheme.typography.headlineMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        } },
        confirmButton = { TextButton(onClick = {
            val url = loginUrl ?: return@TextButton
            loginUrl = null
            try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            catch (_: android.content.ActivityNotFoundException) { actionError = R.string.sync_error_login; run { coordinator.cancelLogin() } }
        }) { Text(stringResource(R.string.sync_login_open_browser)) } },
        dismissButton = { TextButton(onClick = { loginUrl = null; run { coordinator.cancelLogin() } }) {
            Text(stringResource(R.string.sync_cancel_connection))
        } })
    pendingQr?.let { contents ->
        val pairing = SyncPairing.parse(contents)
        AlertDialog(onDismissRequest = { pendingQr = null },
            title = { Text(stringResource(R.string.sync_qr_verify_title)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_qr_verify_summary))
                Text(SyncBrowserConsent.pairingCode(pairing.server, pairing.token), style = MaterialTheme.typography.headlineMedium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            } },
            confirmButton = { TextButton(onClick = { pendingQr = null; run { coordinator.pairQr(contents); coordinator.sync() } }) {
                Text(stringResource(R.string.sync_qr_codes_match))
            } },
            dismissButton = { TextButton(onClick = { pendingQr = null }) { Text(stringResource(R.string.sync_cancel_connection)) } })
    }
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text(stringResource(R.string.sync_disconnect)) },
        text = { Text(stringResource(R.string.sync_disconnect_summary)) },
        confirmButton = { TextButton(onClick = { disconnect = false; run { coordinator.disconnect() } }) { Text(stringResource(R.string.sync_disconnect)) } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text(stringResource(android.R.string.cancel)) } })
}

internal fun collectionLabel(name: String): Int = when (name) {
    "bookmarks" -> R.string.sync_bookmarks; "history" -> R.string.sync_history; "passwords" -> R.string.sync_passwords
    "credit-cards" -> R.string.sync_cards; "addresses" -> R.string.sync_addresses; else -> R.string.sync_forms
}
internal fun syncError(code: String): Int = when {
    code == "browser_login_upgrade_required" -> R.string.sync_login_upgrade_required
    code == "login_cancelled" -> R.string.sync_login_cancelled
    code == "invalid_pairing_qr" -> R.string.sync_invalid_qr
    code == "pairing_expired" -> R.string.sync_qr_expired
    code.startsWith("account_") || code == "invalid_account_capsule" -> R.string.sync_error_account_access
    code.contains("rate_limited") -> R.string.sync_error_rate_limit
    code.contains("recovery") || code.contains("keys") || code.contains("registry") -> R.string.sync_error_keys
    code.contains("auth") || code.contains("login") || code.contains("refresh") -> R.string.sync_error_login
    code.contains("quota") || code.contains("capacity") -> R.string.sync_error_quota
    code.contains("incompatible") || code.contains("unsupported") -> R.string.sync_unsupported
    else -> R.string.sync_error_retry
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SyncAppBar(onBack: () -> Unit) {
    TopAppBar(title = { Text(stringResource(R.string.sync_title)) }, navigationIcon = {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.icons_arrow_backward), stringResource(R.string.sync_back))
        }
    })
}

