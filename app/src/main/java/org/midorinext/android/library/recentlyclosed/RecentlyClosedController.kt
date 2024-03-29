/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.recentlyclosed

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.RecentlyClosedAction
import mozilla.components.browser.state.state.recover.TabState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.recentlyclosed.RecentlyClosedTabsStorage
import mozilla.components.feature.tabs.TabsUseCases
import org.midorinext.android.BrowserDirection
import org.midorinext.android.HomeActivity
import org.midorinext.android.R
import org.midorinext.android.browser.browsingmode.BrowsingMode

@Suppress("TooManyFunctions")
interface RecentlyClosedController {
    fun handleOpen(tab: TabState, mode: BrowsingMode? = null)
    fun handleOpen(tabs: Set<TabState>, mode: BrowsingMode? = null)
    fun handleDelete(tab: TabState)
    fun handleDelete(tabs: Set<TabState>)
    fun handleShare(tabs: Set<TabState>)
    fun handleNavigateToHistory()
    fun handleRestore(item: TabState)
    fun handleSelect(tab: TabState)
    fun handleDeselect(tab: TabState)
    fun handleBackPressed(): Boolean
}

@Suppress("TooManyFunctions", "LongParameterList")
class DefaultRecentlyClosedController(
    private val navController: NavController,
    private val browserStore: BrowserStore,
    private val recentlyClosedStore: RecentlyClosedFragmentStore,
    private val recentlyClosedTabsStorage: RecentlyClosedTabsStorage,
    private val tabsUseCases: TabsUseCases,
    private val activity: HomeActivity,
    private val lifecycleScope: CoroutineScope,
    private val openToBrowser: (url: String, mode: BrowsingMode?) -> Unit
) : RecentlyClosedController {
    override fun handleOpen(tab: TabState, mode: BrowsingMode?) {
        openToBrowser(tab.url, mode)
    }

    override fun handleOpen(tabs: Set<TabState>, mode: BrowsingMode?) {
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
        tabs.forEach { tab -> handleOpen(tab, mode) }
    }

    override fun handleSelect(tab: TabState) {
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.Select(tab))
    }

    override fun handleDeselect(tab: TabState) {
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.Deselect(tab))
    }

    override fun handleDelete(tab: TabState) {
        browserStore.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
    }

    override fun handleDelete(tabs: Set<TabState>) {
        recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
        tabs.forEach { tab ->
            browserStore.dispatch(RecentlyClosedAction.RemoveClosedTabAction(tab))
        }
    }

    override fun handleNavigateToHistory() {
        navController.navigate(
            RecentlyClosedFragmentDirections.actionGlobalHistoryFragment(),
            NavOptions.Builder().setPopUpTo(R.id.historyFragment, true).build()
        )
    }

    override fun handleShare(tabs: Set<TabState>) {
        val shareData = tabs.map { ShareData(url = it.url, title = it.title) }
        navController.navigate(
            RecentlyClosedFragmentDirections.actionGlobalShareFragment(
                data = shareData.toTypedArray()
            )
        )
    }

    override fun handleRestore(item: TabState) {
        lifecycleScope.launch {
            tabsUseCases.restore(item, recentlyClosedTabsStorage.engineStateStorage())

            browserStore.dispatch(
                RecentlyClosedAction.RemoveClosedTabAction(item)
            )

            activity.openToBrowser(
                from = BrowserDirection.FromRecentlyClosed
            )
        }
    }

    override fun handleBackPressed(): Boolean {
        return if (recentlyClosedStore.state.selectedTabs.isNotEmpty()) {
            recentlyClosedStore.dispatch(RecentlyClosedFragmentAction.DeselectAll)
            true
        } else {
            false
        }
    }
}
