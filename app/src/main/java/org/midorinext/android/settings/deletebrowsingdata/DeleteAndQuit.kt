/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.deletebrowsingdata

import android.app.Activity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.midorinext.android.R
import org.midorinext.android.components.MidoriSnackbar
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings

/**
 * Deletes selected browsing data and finishes the activity.
 */
fun deleteAndQuit(activity: Activity, coroutineScope: CoroutineScope, snackbar: MidoriSnackbar?) {
    coroutineScope.launch {
        val settings = activity.settings()
        val controller = DefaultDeleteBrowsingDataController(
            activity.components.useCases.tabsUseCases.removeAllTabs,
            activity.components.useCases.downloadUseCases.removeAllDownloads,
            activity.components.core.historyStorage,
            activity.components.core.permissionStorage,
            activity.components.core.store,
            activity.components.core.icons,
            activity.components.core.engine,
            coroutineContext
        )

        snackbar?.apply {
            setText(activity.getString(R.string.deleting_browsing_data_in_progress))
            duration = Snackbar.LENGTH_INDEFINITE
            show()
        }

        DeleteBrowsingDataOnQuitType.values().map { type ->
            launch {
                if (settings.getDeleteDataOnQuit(type)) {
                    controller.deleteType(type)
                }
            }
        }.joinAll()

        snackbar?.dismiss()

        activity.finishAndRemoveTask()
    }
}

private suspend fun DeleteBrowsingDataController.deleteType(type: DeleteBrowsingDataOnQuitType) {
    when (type) {
        DeleteBrowsingDataOnQuitType.TABS -> deleteTabs()
        DeleteBrowsingDataOnQuitType.HISTORY -> deleteBrowsingData()
        DeleteBrowsingDataOnQuitType.COOKIES -> deleteCookies()
        DeleteBrowsingDataOnQuitType.CACHE -> deleteCachedFiles()
        DeleteBrowsingDataOnQuitType.PERMISSIONS -> withContext(IO) {
            deleteSitePermissions()
        }
        DeleteBrowsingDataOnQuitType.DOWNLOADS -> deleteDownloads()
    }
}
