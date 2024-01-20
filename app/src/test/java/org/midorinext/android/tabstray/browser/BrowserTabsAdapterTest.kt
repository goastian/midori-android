/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.browser.tabstray.TabsAdapter.Companion.PAYLOAD_DONT_HIGHLIGHT_SELECTED_ITEM
import mozilla.components.browser.tabstray.TabsAdapter.Companion.PAYLOAD_HIGHLIGHT_SELECTED_ITEM
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.tabstray.TabsTrayStore
import mozilla.components.browser.state.state.createTab
import org.midorinext.android.ext.components

@RunWith(MidoriRobolectricTestRunner::class)
class BrowserTabsAdapterTest {

    private val context = testContext
    private val interactor = mockk<BrowserTrayInteractor>(relaxed = true)
    private val store = TabsTrayStore()

    @Test
    fun `WHEN bind with payloads is called THEN update the holder`() {
        every { testContext.components.core.thumbnailStorage } returns mockk()
        val adapter = BrowserTabsAdapter(context, interactor, store, mockk())
        val holder = mockk<AbstractBrowserTabViewHolder>(relaxed = true)

        adapter.updateTabs(
            listOf(
                createTab(url = "url", id = "tab1")
            ),
            null,
            selectedTabId = "tab1"
        )

        adapter.onBindViewHolder(holder, 0, listOf(PAYLOAD_HIGHLIGHT_SELECTED_ITEM))

        verify { holder.updateSelectedTabIndicator(true) }

        adapter.onBindViewHolder(holder, 0, listOf(PAYLOAD_DONT_HIGHLIGHT_SELECTED_ITEM))

        verify { holder.updateSelectedTabIndicator(false) }
    }

}
