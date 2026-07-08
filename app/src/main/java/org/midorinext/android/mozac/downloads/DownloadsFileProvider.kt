package org.midorinext.android.mozac.downloads

import android.content.Context
import androidx.core.content.FileProvider
import org.midorinext.android.ext.openFileInApp
import mozilla.components.feature.downloads.R
import java.io.File

class DownloadsFileProvider : FileProvider(R.xml.feature_downloads_file_paths)

fun Context.openDownloadedFile(filePath: String, contentType: String?) {
    this.openFileInApp(
        FileProvider.getUriForFile(
        this,
        "${this.packageName}.feature.downloads.fileprovider",
        File(filePath),
    ), contentType)
}