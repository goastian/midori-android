package org.midorinext.android.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DownloadStorage
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.downloads.INTENT_EXTRA_DOWNLOAD_ID
import mozilla.components.feature.downloads.manager.DownloadManager
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.utils.DownloadFileUtils
import org.midorinext.android.preferences.app.AppPreferencesRepository
import javax.inject.Inject

@HiltViewModel
class DownloadsScreenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BrowserStore,
    private val downloadStorage: DownloadStorage,
    private val downloadsUseCases: DownloadsUseCases,
    private val downloadManager: DownloadManager,
    private val downloadFileUtils: DownloadFileUtils,
    private val appPreferencesRepository: AppPreferencesRepository,
    val fileSizeFormatter: FileSizeFormatter
) : ViewModel() {
    val downloads = store.flow()
        .map { state ->
            state.downloads.values
                .sortedByDescending { it.createdTime }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val storedDownloads = downloadStorage.getDownloads()
        .map { list -> list.sortedByDescending { it.createdTime } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val wifiOnly = appPreferencesRepository.flow
        .map { it.downloadWifiOnly }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    init {
        downloadsUseCases.restoreDownloads()
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateDownloadWifiOnly(enabled)
        }
    }

    fun pause(download: DownloadState) {
        sendDownloadAction(AbstractFetchDownloadService.ACTION_PAUSE, download.id)
    }

    fun resume(download: DownloadState) {
        sendDownloadAction(AbstractFetchDownloadService.ACTION_RESUME, download.id)
    }

    fun cancel(download: DownloadState) {
        sendDownloadAction(AbstractFetchDownloadService.ACTION_CANCEL, download.id)
    }

    fun retry(download: DownloadState) {
        downloadManager.tryAgain(download.id)
    }

    fun open(download: DownloadState) {
        downloadFileUtils.openFile(
            fileName = download.fileName,
            directoryPath = download.directoryPath,
            contentType = download.contentType
        )
    }

    fun remove(download: DownloadState, removeFromDisk: Boolean = false) {
        downloadsUseCases.removeDownload(download.id, removeFromDisk)
    }

    private fun sendDownloadAction(action: String, downloadId: String) {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
            putExtra(INTENT_EXTRA_DOWNLOAD_ID, downloadId)
        }
        context.sendBroadcast(intent)
    }
}
