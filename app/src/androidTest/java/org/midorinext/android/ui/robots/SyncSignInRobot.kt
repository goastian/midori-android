/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.UiSelector
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertTrue
import org.midorinext.android.R
import org.midorinext.android.helpers.TestAssetHelper.waitingTime
import org.midorinext.android.helpers.TestHelper.mDevice
import org.midorinext.android.helpers.TestHelper.packageName
import org.midorinext.android.helpers.click

/**
 * Implementation of Robot Pattern for Sync Sign In sub menu.
 */
class SyncSignInRobot {

    fun verifyAccountSettingsMenuHeader() = assertAccountSettingsMenuHeader()
    fun verifyTurnOnSyncMenu() = assertTurnOnSyncMenu()

    class Transition {
        fun goBack(interact: BrowserRobot.() -> Unit): BrowserRobot.Transition {
            goBackButton().click()

            BrowserRobot().interact()
            return BrowserRobot.Transition()
        }
    }
}

private fun goBackButton() =
    onView(allOf(withContentDescription("Navigate up")))

private fun assertAccountSettingsMenuHeader() {
    // Replaced with the new string here, the test is assuming we are NOT signed in
    // Sync tests in SettingsSyncTest are still TO-DO, so I'm not sure that we have a test for signing into Sync
    onView(withText(R.string.preferences_account_settings))
        .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))
}

private fun assertTurnOnSyncMenu() {
    mDevice.findObject(UiSelector().resourceId("$packageName:id/container")).waitForExists(waitingTime)
    assertTrue(
        mDevice.findObject(
            UiSelector()
                .resourceId("$packageName:id/signInScanButton")
                .resourceId("$packageName:id/signInEmailButton")
        ).waitForExists(waitingTime)
    )
}
