/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.downloads

/**
 * Interactor for the download screen
 * Provides implementations for the DownloadViewInteractor
 */
@SuppressWarnings("TooManyFunctions")
class DownloadInteractor(
    private val downloadController: DownloadController
) : DownloadViewInteractor {
    override fun open(item: DownloadItem) {
        downloadController.handleOpen(item)
    }

    override fun select(item: DownloadItem) {
        downloadController.handleSelect(item)
    }

    override fun deselect(item: DownloadItem) {
        downloadController.handleDeselect(item)
    }

    override fun onBackPressed(): Boolean {
        return downloadController.handleBackPressed()
    }

    override fun onModeSwitched() {
        downloadController.handleModeSwitched()
    }

    override fun onDeleteSome(items: Set<DownloadItem>) {
        downloadController.handleDeleteSome(items)
    }
}
