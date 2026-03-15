/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui.robots

import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertTrue
import org.midorinext.android.R
import org.midorinext.android.helpers.TestAssetHelper.waitingTime
import org.midorinext.android.helpers.TestHelper.getStringResource
import org.midorinext.android.helpers.TestHelper.packageName

/**
 * Implementation of Robot Pattern for Synced Tabs sub menu.
 */
class SyncedTabsRobot {
    fun verifyNotSignedInSyncTabsView() = assertNotSignedInSyncTabsView()

    class Transition {
        fun syncedTabs(interact: SyncedTabsRobot.() -> Unit): SyncedTabsRobot.Transition {
            SyncedTabsRobot().interact()
            return SyncedTabsRobot.Transition()
        }
    }

    private fun assertNotSignedInSyncTabsView() {
        assertTrue(
            mDevice
                .findObject(
                UiSelector()
                    .resourceId("$packageName:id/synced_tabs_status")
                    .textContains(getStringResource(R.string.synced_tabs_connect_to_sync_account)),
            ).waitForExists(waitingTime),
        )
    }
}
