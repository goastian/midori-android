package org.midorinext.android.mozac.downloads

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.state.content.DownloadState
import mozilla.components.feature.downloads.R
import mozilla.components.support.utils.DefaultDownloadFileUtils
import org.midorinext.android.ext.openDownloadWithChooser

class DownloadsFileProvider : FileProvider(R.xml.feature_downloads_file_paths)

suspend fun Context.openDownloadedFile(download: DownloadState) {
    val intent = withContext(Dispatchers.IO) {
        DefaultDownloadFileUtils(this@openDownloadedFile).createOpenFileIntent(
            fileName = download.fileName,
            directoryPath = download.directoryPath,
            downloadContentType = download.contentType,
        )
    }
    openDownloadWithChooser(intent)
}
