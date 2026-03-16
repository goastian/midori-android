/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import mozilla.components.feature.autofill.AutofillConfiguration
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DateTimeProvider
import mozilla.components.support.utils.DefaultDateTimeProvider
import org.midorinext.android.autofill.AutofillConfirmActivity
import org.midorinext.android.autofill.AutofillSearchActivity
import org.midorinext.android.autofill.AutofillUnlockActivity
import org.midorinext.android.components.Analytics
import org.midorinext.android.components.BackgroundServices
import org.midorinext.android.components.Core
import org.midorinext.android.components.Services
import org.midorinext.android.components.UseCases
import org.midorinext.android.components.Utilities

/**
 * Provides access to all components.
 */
class Components(
    private val context: Context,
) {
    val core by lazy { Core(context, analytics.crashReporter) }
    val useCases by lazy {
        UseCases(
            context,
            core.engine,
            core.store,
            core.shortcutManager,
        )
    }

    // Background services are initiated eagerly; they kick off periodic tasks and setup an accounts system.
    val backgroundServices by lazy {
        BackgroundServices(
            context,
            core.lazyHistoryStorage,
            core.lazyRemoteTabsStorage,
            core.lazyLoginsStorage,
        )
    }
    val analytics by lazy { Analytics(context) }
    val utils by lazy {
        Utilities(
            context,
            core.store,
            useCases.sessionUseCases,
            useCases.searchUseCases,
            useCases.tabsUseCases,
            useCases.customTabsUseCases,
        )
    }
    val services by lazy { Services(context, backgroundServices.accountManager, useCases.tabsUseCases) }

    @delegate:SuppressLint("NewApi")
    val autofillConfiguration by lazy {
        AutofillConfiguration(
            storage = core.loginsStorage,
            publicSuffixList = utils.publicSuffixList,
            unlockActivity = AutofillUnlockActivity::class.java,
            confirmActivity = AutofillConfirmActivity::class.java,
            searchActivity = AutofillSearchActivity::class.java,
            applicationName = context.getString(R.string.app_name),
            httpClient = core.client,
        )
    }

    private val notificationManagerCompat = NotificationManagerCompat.from(context)

    val notificationsDelegate: NotificationsDelegate by lazy {
        NotificationsDelegate(
            notificationManagerCompat,
        )
    }

    val fileSizeFormatter: FileSizeFormatter by lazy { DefaultFileSizeFormatter(context.applicationContext) }

    private val dateTimeProvider: DateTimeProvider by lazy { DefaultDateTimeProvider() }

    val downloadEstimator: DownloadEstimator by lazy { DownloadEstimator(dateTimeProvider = dateTimeProvider) }
}
