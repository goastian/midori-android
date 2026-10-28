package org.midorinext.android.sync

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var recovery by remember { mutableStateOf("") }
    var backup by remember { mutableStateOf<String?>(null) }
    var backupSaved by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf(emptyList<SyncConflict>()) }
    var disconnect by remember { mutableStateOf(false) }
    SyncCapturePolicy(state.connected && (backup != null || recovery.isNotEmpty()))
    fun run(block: suspend () -> Unit) { scope.launch { runCatching { block() } } }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents -> run { coordinator.pairQr(contents); coordinator.sync() } }
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
                        val url = coordinator.beginLogin(SyncTransport.DEFAULT_SERVER)
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                        catch (_: android.content.ActivityNotFoundException) { actionError = R.string.sync_error_login }
                    }
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            actionError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            state.error?.let { Text(stringResource(syncError(it)), color = MaterialTheme.colorScheme.error) }
            Text(stringResource(R.string.sync_summary))
            Text(state.name, style = MaterialTheme.typography.titleMedium)
            Text(state.server, style = MaterialTheme.typography.bodySmall)
            if (state.phase == "recovery_required" || state.phase == "backup_required") {
                Text(stringResource(R.string.sync_unlock_summary))
                OutlinedTextField(recovery, { recovery = it }, label = { Text(stringResource(R.string.sync_recovery_code)) }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { run { coordinator.recover(recovery); recovery = ""; coordinator.sync() } }, enabled = !state.busy && recovery.isNotBlank()) { Text(stringResource(R.string.sync_unlock)) }
                if (backup == null) OutlinedButton(onClick = { run { backup = coordinator.prepareKeys() } }, enabled = !state.busy) { Text(stringResource(R.string.sync_create_keys)) }
            }
            backup?.let { code ->
                Text(stringResource(R.string.sync_backup_summary))
                Text(code, style = MaterialTheme.typography.bodyLarge)
                Row { Checkbox(backupSaved, { backupSaved = it }); Text(stringResource(R.string.sync_backup_saved), Modifier.padding(top = 12.dp)) }
                Button(onClick = { run { coordinator.activateKeys(); backup = null; coordinator.sync() } }, enabled = backupSaved && !state.busy) { Text(stringResource(R.string.sync_continue)) }
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
    if (disconnect) AlertDialog(onDismissRequest = { disconnect = false }, title = { Text(stringResource(R.string.sync_disconnect)) },
        text = { Text(stringResource(R.string.sync_disconnect_summary)) },
        confirmButton = { TextButton(onClick = { disconnect = false; run { coordinator.disconnect() } }) { Text(stringResource(R.string.sync_disconnect)) } },
        dismissButton = { TextButton(onClick = { disconnect = false }) { Text(stringResource(android.R.string.cancel)) } })
}

internal fun collectionLabel(name: String): Int = when (name) {
    "bookmarks" -> R.string.sync_bookmarks; "history" -> R.string.sync_history; "passwords" -> R.string.sync_passwords
    "credit-cards" -> R.string.sync_cards; "addresses" -> R.string.sync_addresses; else -> R.string.sync_forms
}
private fun syncError(code: String): Int = when {
    code == "invalid_pairing_qr" -> R.string.sync_invalid_qr
    code == "pairing_expired" -> R.string.sync_qr_expired
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

@Composable
internal fun SyncCapturePolicy(sensitive: Boolean) {
    val window = (LocalContext.current as Activity).window
    DisposableEffect(window, sensitive) {
        if (sensitive) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
