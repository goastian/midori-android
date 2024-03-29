/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.logins.controller

import androidx.navigation.NavController
import org.midorinext.android.BrowserDirection
import org.midorinext.android.settings.SupportUtils
import org.midorinext.android.settings.logins.LoginsAction
import org.midorinext.android.settings.logins.LoginsFragmentStore
import org.midorinext.android.settings.logins.SavedLogin
import org.midorinext.android.settings.logins.SortingStrategy
import org.midorinext.android.settings.logins.fragment.SavedLoginsFragmentDirections
import org.midorinext.android.utils.Settings

/**
 * Controller for the saved logins list
 *
 * @param loginsFragmentStore Store used to hold in-memory collection state.
 * @param navController NavController manages app navigation within a NavHost.
 * @param browserNavigator Controller allowing browser navigation to any Uri.
 * @param settings SharedPreferences wrapper for easier usage.
 */
class LoginsListController(
    private val loginsFragmentStore: LoginsFragmentStore,
    private val navController: NavController,
    private val browserNavigator: (
        searchTermOrURL: String,
        newTab: Boolean,
        from: BrowserDirection
    ) -> Unit,
    private val settings: Settings,
) {

    fun handleItemClicked(item: SavedLogin) {
        loginsFragmentStore.dispatch(LoginsAction.LoginSelected(item))
        navController.navigate(
            SavedLoginsFragmentDirections.actionSavedLoginsFragmentToLoginDetailFragment(item.guid)
        )
    }

    fun handleAddLoginClicked() {
        navController.navigate(
            SavedLoginsFragmentDirections.actionSavedLoginsFragmentToAddLoginFragment()
        )
    }

    fun handleLearnMoreClicked() {
        browserNavigator.invoke(
            SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SYNC_SETUP),
            true,
            BrowserDirection.FromSavedLoginsFragment
        )
    }

    fun handleSort(sortingStrategy: SortingStrategy) {
        loginsFragmentStore.dispatch(
            LoginsAction.SortLogins(
                sortingStrategy
            )
        )
        settings.savedLoginsSortingStrategy = sortingStrategy
    }
}
