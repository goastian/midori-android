/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import android.content.Context
import androidx.annotation.VisibleForTesting
import mozilla.components.browser.menu.BrowserMenuBuilder
import org.midorinext.android.tabstray.NavigationInteractor
import org.midorinext.android.tabstray.TabsTrayAction
import org.midorinext.android.tabstray.TabsTrayInteractor
import org.midorinext.android.tabstray.TabsTrayStore
import org.midorinext.android.utils.Do

class SelectionMenuIntegration(
    private val context: Context,
    private val store: TabsTrayStore,
    private val navInteractor: NavigationInteractor,
    private val trayInteractor: TabsTrayInteractor
) {
    private val menu by lazy {
        SelectionMenu(context, ::handleMenuClicked)
    }

    /**
     * Builds the internal menu items list. See [BrowserMenuBuilder.build].
     */
    fun build() = menu.menuBuilder.build(context)

    @VisibleForTesting
    internal fun handleMenuClicked(item: SelectionMenu.Item) {
        Do exhaustive when (item) {
            is SelectionMenu.Item.BookmarkTabs -> {
                navInteractor.onSaveToBookmarks(store.state.mode.selectedTabs)
            }
            is SelectionMenu.Item.DeleteTabs -> {
                trayInteractor.onDeleteTabs(store.state.mode.selectedTabs)
            }
            is SelectionMenu.Item.MakeInactive -> {
                trayInteractor.onInactiveDebugClicked(store.state.mode.selectedTabs)
            }
        }
        store.dispatch(TabsTrayAction.ExitSelectMode)
    }
}
