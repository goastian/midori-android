/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tabstray.browser

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.support.test.robolectric.testContext
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.ext.components
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.tabstray.TabsTrayStore

@RunWith(MidoriRobolectricTestRunner::class)
class AbstractBrowserTrayListTest {

    @Test
    fun `WHEN recyclerview detaches from window THEN notify adapter`() {
        every { testContext.components.core.store } returns BrowserStore()
        val trayList = PrivateBrowserTrayList(testContext)
        val adapter = mockk<BrowserTabsAdapter>(relaxed = true)

        trayList.adapter = adapter
        trayList.tabsTrayStore = TabsTrayStore()

        trayList.onDetachedFromWindow()

        verify { adapter.onDetachedFromRecyclerView(trayList) }
    }
}
