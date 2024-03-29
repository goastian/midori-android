/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.customannotations.SmokeTest
import org.midorinext.android.helpers.AndroidAssetDispatcher
import org.midorinext.android.helpers.Constants.PackageName.GOOGLE_PLAY_SERVICES
import org.midorinext.android.helpers.FeatureSettingsHelper
import org.midorinext.android.helpers.HomeActivityIntentTestRule
import org.midorinext.android.helpers.TestAssetHelper
import org.midorinext.android.helpers.TestHelper.assertNativeAppOpens
import org.midorinext.android.ui.robots.homeScreen
import org.midorinext.android.ui.robots.navigationToolbar

/**
 *  Tests for verifying the advanced section in Settings
 *
 */

class SettingsAdvancedTest {
    /* ktlint-disable no-blank-line-before-rbrace */ // This imposes unreadable grouping.

    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer
    private val featureSettingsHelper = FeatureSettingsHelper()

    @get:Rule
    val activityIntentTestRule = HomeActivityIntentTestRule()

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }
        featureSettingsHelper.setTCPCFREnabled(false)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()

        featureSettingsHelper.resetAllFeatureFlags()
    }

    @Test
    // Walks through settings menu and sub-menus to ensure all items are present
    fun settingsAboutItemsTest() {
        // ADVANCED
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
            // ADVANCED
            verifyAdvancedHeading()
            verifyAddons()
            verifyOpenLinksInAppsButton()
            verifyOpenLinksInAppsSwitchState(false)
            verifyRemoteDebug()
            verifyLeakCanaryButton()
        }
    }

    @SmokeTest
    @Test
    // Assumes Play Store is installed and enabled
    fun openLinkInAppTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 3)
        val playStoreUrl = "play.google.com/store/apps/details?id=org.midorinext.android"

        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
            verifyOpenLinksInAppsSwitchState(false)
            clickOpenLinksInAppsSwitch()
            verifyOpenLinksInAppsSwitchState(true)
        }.goBack {}

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
            mDevice.waitForIdle()
            clickLinkMatchingText("Mozilla Playstore link")
            mDevice.waitForIdle()
            assertNativeAppOpens(GOOGLE_PLAY_SERVICES, playStoreUrl)
        }
    }
}
