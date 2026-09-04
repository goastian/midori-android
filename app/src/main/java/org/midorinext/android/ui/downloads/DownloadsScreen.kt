package org.midorinext.android.ui.downloads

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mozilla.components.browser.state.state.content.DownloadState
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import org.midorinext.android.ui.widgets.ScreenHeader
import org.midorinext.android.preferences.app.DownloadRemovalBehavior
import kotlin.math.max

@Composable
fun DownloadsScreen(
    onClose: () -> Unit,
    viewModel: DownloadsScreenViewModel = hiltViewModel()
) {
    val activeDownloads by viewModel.downloads.collectAsStateWithLifecycle()
    val storedDownloads by viewModel.storedDownloads.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val removalBehavior by viewModel.removalBehavior.collectAsStateWithLifecycle()
    val downloadPendingRemoval = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<DownloadState?>(null)
    }

    val downloads = remember(activeDownloads, storedDownloads) {
        (activeDownloads + storedDownloads)
            .distinctBy { it.id }
            .sortedByDescending { it.createdTime }
    }

    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ScreenHeader(title = stringResource(R.string.browser_downloads))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.download_wifi_only),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.download_wifi_only_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = wifiOnly, onCheckedChange = viewModel::updateWifiOnly)
        }

        if (downloads.isEmpty()) {
            EmptyPagePlaceholder(
                icon = R.drawable.icons_download,
                title = stringResource(R.string.downloads_empty_title),
                subtitle = stringResource(R.string.downloads_empty_summary)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    DownloadCard(
                        download = download,
                        viewModel = viewModel,
                        removalBehavior = removalBehavior,
                        onAskToRemove = { downloadPendingRemoval.value = it }
                    )
                }
            }
        }

        downloadPendingRemoval.value?.let { download ->
            AlertDialog(
                onDismissRequest = { downloadPendingRemoval.value = null },
                title = { Text(stringResource(R.string.download_removal_confirm_title)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.remove(download, removeFromDisk = true)
                        downloadPendingRemoval.value = null
                    }) {
                        Text(stringResource(R.string.download_removal_confirm_delete))
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            viewModel.remove(download, removeFromDisk = false)
                            downloadPendingRemoval.value = null
                        }) {
                            Text(stringResource(R.string.download_removal_confirm_history))
                        }
                        TextButton(onClick = { downloadPendingRemoval.value = null }) {
                            Text(stringResource(R.string.download_removal_cancel))
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DownloadCard(
    download: DownloadState,
    viewModel: DownloadsScreenViewModel,
    removalBehavior: DownloadRemovalBehavior,
    onAskToRemove: (DownloadState) -> Unit,
) {
    val progress = download.progress
    val statusText = downloadStatusText(download, viewModel)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = download.fileName ?: download.url,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (download.status == DownloadState.Status.DOWNLOADING) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                when (download.status) {
                    DownloadState.Status.DOWNLOADING -> {
                        AssistChip(
                            onClick = { viewModel.pause(download) },
                            label = { Text(stringResource(R.string.download_pause)) }
                        )
                        AssistChip(
                            onClick = { viewModel.cancel(download) },
                            label = { Text(stringResource(R.string.download_cancel)) }
                        )
                    }
                    DownloadState.Status.PAUSED -> {
                        AssistChip(
                            onClick = { viewModel.resume(download) },
                            label = { Text(stringResource(R.string.download_resume)) }
                        )
                        AssistChip(
                            onClick = { viewModel.cancel(download) },
                            label = { Text(stringResource(R.string.download_cancel)) }
                        )
                    }
                    DownloadState.Status.FAILED -> {
                        AssistChip(
                            onClick = { viewModel.retry(download) },
                            label = { Text(stringResource(R.string.retry_download)) }
                        )
                        TextButton(onClick = {
                            removeDownload(download, removalBehavior, viewModel, onAskToRemove)
                        }) {
                            Text(stringResource(R.string.download_remove))
                        }
                    }
                    DownloadState.Status.COMPLETED -> {
                        AssistChip(
                            onClick = { viewModel.open(download) },
                            label = { Text(stringResource(R.string.download_open)) }
                        )
                        TextButton(onClick = {
                            removeDownload(download, removalBehavior, viewModel, onAskToRemove)
                        }) {
                            Text(stringResource(R.string.download_remove))
                        }
                    }
                    DownloadState.Status.CANCELLED,
                    DownloadState.Status.INITIATED -> {
                        TextButton(onClick = {
                            removeDownload(download, removalBehavior, viewModel, onAskToRemove)
                        }) {
                            Text(stringResource(R.string.download_remove))
                        }
                    }
                }
            }
        }
    }
}

private fun removeDownload(
    download: DownloadState,
    behavior: DownloadRemovalBehavior,
    viewModel: DownloadsScreenViewModel,
    onAskToRemove: (DownloadState) -> Unit,
) {
    when (behavior) {
        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_DELETE_FROM_DEVICE -> {
            viewModel.remove(download, removeFromDisk = true)
        }
        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_REMOVE_FROM_HISTORY -> {
            viewModel.remove(download, removeFromDisk = false)
        }
        DownloadRemovalBehavior.DOWNLOAD_REMOVAL_ASK,
        DownloadRemovalBehavior.UNRECOGNIZED -> onAskToRemove(download)
    }
}

@Composable
private fun downloadStatusText(
    download: DownloadState,
    viewModel: DownloadsScreenViewModel
): String {
    val copied = viewModel.fileSizeFormatter.formatSizeInBytes(download.currentBytesCopied)
    val total = download.contentLength?.let { viewModel.fileSizeFormatter.formatSizeInBytes(it) }
    val progress = if (total != null) "$copied / $total" else copied

    return when (download.status) {
        DownloadState.Status.DOWNLOADING -> {
            val eta = estimateEta(download)
            listOfNotNull(
                stringResource(R.string.download_status_downloading),
                progress,
                eta?.let { stringResource(R.string.download_eta, it) }
            ).joinToString(" - ")
        }
        DownloadState.Status.PAUSED -> stringResource(R.string.download_status_paused, progress)
        DownloadState.Status.FAILED -> stringResource(R.string.download_status_failed)
        DownloadState.Status.COMPLETED -> stringResource(R.string.download_status_completed, progress)
        DownloadState.Status.CANCELLED -> stringResource(R.string.download_status_cancelled)
        DownloadState.Status.INITIATED -> stringResource(R.string.download_status_pending)
    }
}

private fun estimateEta(download: DownloadState): String? {
    val total = download.contentLength ?: return null
    val remaining = total - download.currentBytesCopied
    if (remaining <= 0L || download.currentBytesCopied <= 0L) return null

    val elapsedMs = max(1L, System.currentTimeMillis() - download.createdTime)
    val bytesPerSecond = download.currentBytesCopied * 1000L / elapsedMs
    if (bytesPerSecond <= 0L) return null

    val seconds = remaining / bytesPerSecond
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${rest}s"
    } else {
        "${rest}s"
    }
}
