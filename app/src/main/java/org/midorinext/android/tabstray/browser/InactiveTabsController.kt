/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import org.midorinext.android.components.AppStore
import org.midorinext.android.components.appstate.AppAction
import org.midorinext.android.components.appstate.AppAction.UpdateInactiveExpanded
import org.midorinext.android.ext.potentialInactiveTabs
import org.midorinext.android.utils.Settings

/**
 * Contract for how all user interactions with the Inactive Tabs feature are to be handled.
 */
interface InactiveTabsController {

    /**
     * Opens the given inactive tab.
     */
    fun openInactiveTab(tab: TabSessionState)

    /**
     * Closes the given inactive tab.
     */
    fun closeInactiveTab(tab: TabSessionState)

    /**
     * Updates the inactive card to be expanded to display all the tabs, or collapsed with only
     * the title showing.
     */
    fun updateCardExpansion(isExpanded: Boolean)

    /**
     * Dismiss the auto-close dialog.
     */
    fun dismissAutoCloseDialog()

    /**
     * Enable the auto-close feature with the "after a month" setting.
     */
    fun enableInactiveTabsAutoClose()

    /**
     * Delete all inactive tabs.
     */
    fun deleteAllInactiveTabs()
}

/**
 * Default behavior for handling all user interactions with the Inactive Tabs feature.
 *
 * @param appStore [AppStore] used to dispatch any [AppAction].
 * @param settings [Settings] used to update any user preferences.
 * @param browserStore [BrowserStore] used to obtain all inactive tabs.
 * @param tabsUseCases [TabsUseCases] used to perform the deletion of all inactive tabs.
 * @param showUndoSnackbar Invoked when deleting all inactive tabs.
 */
class DefaultInactiveTabsController(
    private val appStore: AppStore,
    private val settings: Settings,
    private val browserStore: BrowserStore,
    private val tabsUseCases: TabsUseCases,
    private val showUndoSnackbar: (Boolean) -> Unit,
) : InactiveTabsController {

    override fun openInactiveTab(tab: TabSessionState) {
    }

    override fun closeInactiveTab(tab: TabSessionState) {
    }

    override fun updateCardExpansion(isExpanded: Boolean) {
        appStore.dispatch(UpdateInactiveExpanded(isExpanded))
    }

    override fun dismissAutoCloseDialog() {
        markDialogAsShown()
    }

    override fun enableInactiveTabsAutoClose() {
        markDialogAsShown()
        settings.closeTabsAfterOneMonth = true
        settings.closeTabsAfterOneWeek = false
        settings.closeTabsAfterOneDay = false
        settings.manuallyCloseTabs = false
    }

    override fun deleteAllInactiveTabs() {
        browserStore.state.potentialInactiveTabs.map { it.id }.let {
            tabsUseCases.removeTabs(it)
        }
        showUndoSnackbar(false)
    }

    /**
     * Marks the dialog as shown and to not be displayed again.
     */
    private fun markDialogAsShown() {
        settings.hasInactiveTabsAutoCloseDialogBeenDismissed = true
    }
}
