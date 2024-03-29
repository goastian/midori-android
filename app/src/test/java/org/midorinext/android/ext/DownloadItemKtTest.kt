/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ext

import mozilla.components.browser.state.state.content.DownloadState
import org.junit.Test

import org.junit.Assert.assertEquals
import org.midorinext.android.R
import org.midorinext.android.library.downloads.DownloadItem

class DownloadItemKtTest {
    @Test
    fun getIcon() {
        val downloadItem = DownloadItem(
            id = "0",
            url = "url",
            fileName = "MyAwesomeFile",
            filePath = "",
            size = "",
            contentType = "image/png",
            status = DownloadState.Status.COMPLETED
        )

        assertEquals(R.drawable.ic_file_type_image, downloadItem.getIcon())
        assertEquals(R.drawable.ic_file_type_audio_note, downloadItem.copy(contentType = "audio/mp3").getIcon())
        assertEquals(R.drawable.ic_file_type_video, downloadItem.copy(contentType = "video/mp4").getIcon())
        assertEquals(R.drawable.ic_file_type_document, downloadItem.copy(contentType = "text/csv").getIcon())
        assertEquals(R.drawable.ic_file_type_zip, downloadItem.copy(contentType = "application/gzip").getIcon())
        assertEquals(R.drawable.ic_file_type_apk, downloadItem.copy(contentType = null, fileName = "Midori.apk").getIcon())
        assertEquals(R.drawable.ic_file_type_zip, downloadItem.copy(contentType = null, fileName = "Midori.zip").getIcon())
        assertEquals(R.drawable.ic_file_type_document, downloadItem.copy(contentType = null, fileName = "Midori.pdf").getIcon())
        assertEquals(R.drawable.ic_file_type_default, downloadItem.copy(contentType = null, fileName = null).getIcon())
    }
}
