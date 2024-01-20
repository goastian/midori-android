/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import kotlinx.coroutines.flow.Flow
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.tabstray.TabsTray
import mozilla.components.lib.state.helpers.AbstractBinding
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifChanged
import org.midorinext.android.tabstray.TabsTrayState
import org.midorinext.android.tabstray.TabsTrayStore

/**
 * A normal tabs observer that updates the provided [TabsTray].
 */
class NormalTabsBinding(
    store: TabsTrayStore,
    private val browserStore: BrowserStore,
    private val tabsTray: TabsTray
) : AbstractBinding<TabsTrayState>(store) {
    override suspend fun onState(flow: Flow<TabsTrayState>) {
        flow.ifChanged { it.normalTabs }
            .collect {
                tabsTray.updateTabs(it.normalTabs, null, browserStore.state.selectedTabId)
            }
    }
}