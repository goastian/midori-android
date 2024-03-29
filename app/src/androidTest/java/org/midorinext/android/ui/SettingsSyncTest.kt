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

class SettingsSyncTest {
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
    // Walks through settings sync menu and sub-menus to ensure all items are present
    fun settingsSyncItemsTest() {
        // SYNC

        // Open 3dot (main) menu
        // Select settings
        // Verify header: "Turn on Sync"
        // Verify description: "Sync bookmarks, history, and more with your Astian Account"
    }

    // SYNC
    @Ignore("This is a stub test, ignore for now")
    @Test
    fun turnOnSync() {
        // Note this requires a test Midori Account and a desktop
        // Open 3dot (main) menu
        // Select settings
        // Click on "Turn on Sync"
        // Open Midori on laptop and go to https://firefox.com/pair
        // Pair with QR code and/or alternate method
        // Verify pairing
    }
}
