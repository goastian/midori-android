package org.midorinext.android.ui.preferences

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.midorinext.android.R
import org.midorinext.android.ext.openAppSystemSettings
import org.midorinext.android.mozac.downloads.hasPersistedWritableTreePermission

@Composable
internal fun NotificationSettingsPreference() {
    val context = LocalContext.current
    val notifications = remember(context) { NotificationManagerCompat.from(context) }
    var enabled by remember(notifications) { mutableStateOf(notifications.areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        enabled = notifications.areNotificationsEnabled()
    }
    SettingsNavRow(
        label = R.string.settings_notifications_title,
        description = stringResource(if (enabled) R.string.settings_on else R.string.settings_off),
        leadingIcon = R.drawable.icons_notifications,
        trailingIcon = R.drawable.icons_open,
        onClicked = {
            try {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                })
            } catch (_: ActivityNotFoundException) {
                context.openAppSystemSettings()
            }
        },
    )
}

@Composable
internal fun DownloadFolderPreference(
    savedDirectoryUri: String,
    onDirectoryChanged: (String) -> Unit,
    showDefaultAction: Boolean = false,
) {
    val context = LocalContext.current
    var selected by remember(context, savedDirectoryUri) {
        mutableStateOf(context.downloadTreeUri(savedDirectoryUri))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        selected = context.downloadTreeUri(savedDirectoryUri)
    }
    val directoryName by produceState<String?>(null, context, selected) {
        value = selected?.let { tree ->
            withContext(Dispatchers.IO) { context.downloadFolderName(tree) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                onDirectoryChanged(uri.toString())
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.settings_download_folder_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    SettingsNavRow(
        label = R.string.settings_download_folder,
        description = directoryName
            ?: selected?.let { DocumentsContract.getTreeDocumentId(it).substringAfter(':').trim('/') }
                ?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.download_settings_default_location_summary),
        leadingIcon = R.drawable.icons_folder,
        onClicked = {
            val initialFolder = selected?.documentUri() ?: DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:${Environment.DIRECTORY_DOCUMENTS}",
            )
            try {
                picker.launch(initialFolder)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_download_folder_picker_unavailable, Toast.LENGTH_LONG).show()
            }
        },
    )
    if (showDefaultAction && savedDirectoryUri.isNotEmpty()) {
        SettingsNavRow(
            label = R.string.download_settings_use_android_downloads,
            onClicked = { onDirectoryChanged("") },
        )
    }
}

private fun Context.downloadTreeUri(savedDirectoryUri: String): Uri? =
    savedDirectoryUri.takeIf(this::hasPersistedWritableTreePermission)?.let(Uri::parse)

private fun Uri.documentUri(): Uri =
    DocumentsContract.buildDocumentUriUsingTree(this, DocumentsContract.getTreeDocumentId(this))

private fun Context.downloadFolderName(tree: Uri): String? = runCatching {
    val column = DocumentsContract.Document.COLUMN_DISPLAY_NAME
    contentResolver.query(tree.documentUri(), arrayOf(column), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull()
