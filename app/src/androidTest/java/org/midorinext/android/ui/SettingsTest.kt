/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.Rule
import org.junit.Before
import org.junit.After
import org.junit.Ignore
import org.junit.Test
import org.midorinext.android.helpers.AndroidAssetDispatcher
import org.midorinext.android.helpers.HomeActivityTestRule

/**
 *  Tests for verifying the main three dot menu options
 *
 */

class SettingsTest {
    /* ktlint-disable no-blank-line-before-rbrace */ // This imposes unreadable grouping.

    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer

    @get:Rule
    val activityTestRule = HomeActivityTestRule()

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Ignore("This is a stub test, ignore for now")
    @Test
    // Walks through settings menu and sub-menus to ensure all items are present
    fun settingsMenusItemsTest() {
        // SYNC

        // see: SettingsSyncTest

        // BASICS

        // see: SettingsBasicsTest

        // PRIVACY

        // see: SettingsPrivacyTest

        // DEVELOPER TOOLS

        // Verify header: "Developer Tools"
        // Verify item: "Remote debugging via USB" and default toggle value: "Off"

        // ABOUT

        // Verify header: "About"
        // Verify item: "Help"
        // Verify item: "Rate on Google Play"
        // Verify item: "About Midori Preview"
        //
    }

    // SYNC
    // see: SettingsSyncTest

    // BASICS
    // see: SettingsBasicsTest
    //
    // PRIVACY
    // see: SettingsPrivacyTest

    // DEVELOPER TOOLS
    @Ignore("This is a stub test, ignore for now")
    @Test
    fun turnOnRemoteDebuggingViaUsb() {
        // Open terminal
        // Verify USB debugging is off
        // Open 3dot (main) menu
        // Select settings
        // Toggle Remote debugging via USB to 'on'
        // Open terminal
        // Verify USB debugging is on
    }

    // ABOUT
    @Ignore("This is a stub test, ignore for now")
    @Test
    fun verifyHelpRedirect() {
        // Open 3dot (main) menu
        // Select settings
        // Click on "Help"
        // Verify redirect to: https://support.mozilla.org/
    }

    @Ignore("This is a stub test, ignore for now")
    @Test
    fun verifyRateOnGooglePlayRedirect() {
        // Open 3dot (main) menu
        // Select settings
        // Click on "Rate on Google Play"
        // Verify Android "Open with Google Play Store" sub menu
    }

    @Ignore("This is a stub test, ignore for now")
    @Test
    fun verifyAboutMidoriPreview() {
        // Open 3dot (main) menu
        // Select settings
        // Click on "Verify About Midori Preview"
        // Verify about page contains....
        // Build #
        // Version #
        // "Midori Preview is produced by Astian, Inc"
        // Day, Date, timestamp
        // "Open source libraries we use"
    }
}
