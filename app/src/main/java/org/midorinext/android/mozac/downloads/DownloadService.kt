/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.mozac.downloads

import dagger.hilt.android.AndroidEntryPoint
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.downloads.AbstractFetchDownloadService
import mozilla.components.feature.downloads.DefaultPackageNameProvider
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.downloads.PackageNameProvider
import mozilla.components.feature.downloads.filewriter.DefaultDownloadFileWriter
import mozilla.components.feature.downloads.filewriter.DownloadFileWriter
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DownloadFileUtils
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService: AbstractFetchDownloadService() {
    @Inject lateinit var c: dagger.Lazy<Client>
    override val httpClient: Client by lazy { c.get() }

    @Inject lateinit var fsf: dagger.Lazy<FileSizeFormatter>
    override val fileSizeFormatter: FileSizeFormatter by lazy { fsf.get() }

    @Inject lateinit var de: dagger.Lazy<DownloadEstimator>
    override val downloadEstimator: DownloadEstimator by lazy { de.get() }

    @Inject lateinit var s: dagger.Lazy<BrowserStore>
    override val store: BrowserStore by lazy { s.get() }

    @Inject lateinit var nd: dagger.Lazy<NotificationsDelegate>
    override val notificationsDelegate: NotificationsDelegate by lazy { nd.get() }

    @Inject lateinit var dfu: dagger.Lazy<DownloadFileUtils>
    override val downloadFileUtils: DownloadFileUtils by lazy { dfu.get() }

    override val downloadFileWriter: DownloadFileWriter by lazy {
        DefaultDownloadFileWriter(this, downloadFileUtils)
    }

    override val packageNameProvider: PackageNameProvider = DefaultPackageNameProvider(this)
}
