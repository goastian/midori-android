/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui.robots

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.UiSelector
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.midorinext.android.R
import org.midorinext.android.helpers.TestAssetHelper.waitingTime
import org.midorinext.android.helpers.TestHelper.mDevice
import org.midorinext.android.helpers.TestAssetHelper.waitingTimeShort
import org.midorinext.android.helpers.TestHelper.getStringResource
import org.midorinext.android.helpers.TestHelper.packageName
import org.midorinext.android.helpers.assertIsChecked
import org.midorinext.android.helpers.click

/**
 * Implementation of Robot Pattern for the settings Site Permissions sub menu.
 */
class SettingsSubMenuSitePermissionsCommonRobot {

    fun verifyNavigationToolBarHeader(header: String) = assertNavigationToolBarHeader(header)

    fun verifyBlockAudioAndVideoOnMobileDataOnlyAudioAndVideoWillPlayOnWiFi() = assertBlockAudioAndVideoOnMobileDataOnlyAudioAndVideoWillPlayOnWiFi()

    fun verifyBlockAudioOnly() = assertBlockAudioOnly()

    fun verifyVideoAndAudioBlockedRecommended() = assertVideoAndAudioBlockedRecommended()

    fun verifyCheckAutoPlayRadioButtonDefault() = assertCheckAutoPayRadioButtonDefault()

    fun verifyassertAskToAllowRecommended() = assertAskToAllowRecommended()

    fun verifyassertBlocked() = assertBlocked()

    fun verifyCheckCommonRadioButtonDefault() = assertCheckCommonRadioButtonDefault()

    fun verifyBlockedByAndroid() = assertBlockedByAndroid()

    fun verifyUnblockedByAndroid() = assertUnblockedByAndroid()

    fun verifyToAllowIt() = assertToAllowIt()

    fun verifyGotoAndroidSettings() = assertGotoAndroidSettings()

    fun verifyTapPermissions() = assertTapPermissions()

    fun verifyToggleNameToON(name: String) = assertToggleNameToON(name)

    fun verifyGoToSettingsButton() = assertGoToSettingsButton()

    fun verifySitePermissionsAutoPlaySubMenuItems() {
        verifyBlockAudioAndVideoOnMobileDataOnlyAudioAndVideoWillPlayOnWiFi()
        verifyBlockAudioOnly()
        verifyVideoAndAudioBlockedRecommended()
        verifyCheckAutoPlayRadioButtonDefault()
    }

    fun verifySitePermissionsCommonSubMenuItems() {
        verifyassertAskToAllowRecommended()
        verifyassertBlocked()
        verifyCheckCommonRadioButtonDefault()
        verifyBlockedByAndroid()
        verifyToAllowIt()
        verifyGotoAndroidSettings()
        verifyTapPermissions()
        verifyGoToSettingsButton()
    }

    fun verifySitePermissionsNotificationSubMenuItems() {
        verifyassertAskToAllowRecommended()
        verifyassertBlocked()
        verifyCheckCommonRadioButtonDefault()
    }

    fun verifySitePermissionsPersistentStorageSubMenuItems() {
        verifyassertAskToAllowRecommended()
        verifyassertBlocked()
        verifyCheckCommonRadioButtonDefault()
    }

    fun clickGoToSettingsButton() {
        goToSettingsButton().click()
        mDevice.findObject(UiSelector().resourceId("com.android.settings:id/list"))
            .waitForExists(waitingTime)
    }

    fun openAppSystemPermissionsSettings() {
        mDevice.findObject(UiSelector().textContains("Permissions")).click()
    }

    fun switchAppPermissionSystemSetting(permissionCategory: String, permission: String) {
        mDevice.findObject(UiSelector().textContains(permissionCategory)).click()

        if (permission == "Allow") {
            mDevice.findObject(UiSelector().textContains("Allow")).click()
        } else {
            mDevice.findObject(UiSelector().textContains("Deny")).click()
        }
    }

    fun goBackToSystemAppPermissionSettings() {
        mDevice.pressBack()
        mDevice.waitForIdle(waitingTime)
    }

    fun goBackToPermissionsSettingsSubMenu() {
        while (!permissionSettingMenu().waitForExists(waitingTimeShort)) {
            mDevice.pressBack()
            mDevice.waitForIdle(waitingTime)
        }
    }

    fun verifySystemGrantedPermission(permissionCategory: String) {
        assertTrue(
            mDevice.findObject(
                UiSelector().className("android.widget.RelativeLayout")
            ).getChild(
                UiSelector()
                    .resourceId("android:id/title")
                    .textContains(permissionCategory)
            ).waitForExists(waitingTime)
        )

        assertTrue(
            mDevice.findObject(
                UiSelector().className("android.widget.RelativeLayout")
            ).getChild(
                UiSelector()
                    .resourceId("android:id/summary")
                    .textContains("Only while app is in use")
            ).waitForExists(waitingTime)
        )
    }

    class Transition {
        fun goBack(interact: SettingsSubMenuSitePermissionsRobot.() -> Unit): SettingsSubMenuSitePermissionsRobot.Transition {
            goBackButton().click()

            SettingsSubMenuSitePermissionsRobot().interact()
            return SettingsSubMenuSitePermissionsRobot.Transition()
        }
    }
}

private fun assertNavigationToolBarHeader(header: String) = onView(allOf(withContentDescription(header)))

private fun assertBlockAudioAndVideoOnMobileDataOnlyAudioAndVideoWillPlayOnWiFi() =
    onView(withId(R.id.block_radio))
        .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))

private fun assertBlockAudioOnly() = onView(withId(R.id.third_radio))
    .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))

private fun assertVideoAndAudioBlockedRecommended() = onView(withId(R.id.fourth_radio))
    .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))

private fun assertCheckAutoPayRadioButtonDefault() {

    // Allow audio and video
    onView(withId(R.id.block_radio))
        .assertIsChecked(isChecked = false)

    // Block audio and video on cellular data only
    onView(withId(R.id.block_radio))
        .assertIsChecked(isChecked = false)

    // Block audio only
    onView(withId(R.id.third_radio))
        .assertIsChecked(isChecked = true)

    // Block audio and video
    onView(withId(R.id.fourth_radio))
        .assertIsChecked(isChecked = false)
}

private fun assertAskToAllowRecommended() = onView(withId(R.id.ask_to_allow_radio))
    .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))

private fun assertBlocked() = onView(withId(R.id.block_radio))
    .check((matches(withEffectiveVisibility(Visibility.VISIBLE))))

private fun assertCheckCommonRadioButtonDefault() {
    onView(withId(R.id.ask_to_allow_radio)).assertIsChecked(isChecked = true)
    onView(withId(R.id.block_radio)).assertIsChecked(isChecked = false)
}

private fun assertBlockedByAndroid() {
    blockedByAndroidContainer().waitForExists(waitingTime)
    assertTrue(
        mDevice.findObject(
            UiSelector().textContains(getStringResource(R.string.phone_feature_blocked_by_android))
        ).waitForExists(waitingTimeShort)
    )
}

private fun assertUnblockedByAndroid() {
    blockedByAndroidContainer().waitUntilGone(waitingTime)
    assertFalse(
        mDevice.findObject(
            UiSelector().textContains(getStringResource(R.string.phone_feature_blocked_by_android))
        ).waitForExists(waitingTimeShort)
    )
}

private fun blockedByAndroidContainer() = mDevice.findObject(UiSelector().resourceId("$packageName:id/permissions_blocked_container"))

private fun permissionSettingMenu() = mDevice.findObject(UiSelector().resourceId("$packageName:id/container"))

private fun assertToAllowIt() = onView(withText(R.string.phone_feature_blocked_intro))
    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

private fun assertGotoAndroidSettings() = onView(withText(R.string.phone_feature_blocked_step_settings))
    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

private fun assertTapPermissions() = onView(withText("2. Tap Permissions"))
    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

private fun assertToggleNameToON(name: String) = onView(withText(name))
    .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

private fun assertGoToSettingsButton() =
    goToSettingsButton().check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

private fun goBackButton() =
    onView(allOf(withContentDescription("Navigate up")))

private fun goToSettingsButton() = onView(withId(R.id.settings_button))
