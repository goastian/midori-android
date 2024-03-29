/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import android.os.Build
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.net.toUri
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.permission.PermissionRequester
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.midorinext.android.customannotations.SmokeTest
import org.midorinext.android.helpers.FeatureSettingsHelper
import org.midorinext.android.helpers.HomeActivityIntentTestRule
import org.midorinext.android.helpers.TestHelper.deleteDownloadFromStorage
import org.midorinext.android.ui.robots.browserScreen
import org.midorinext.android.ui.robots.downloadRobot
import org.midorinext.android.ui.robots.navigationToolbar
import org.midorinext.android.ui.robots.notificationShade

/**
 *  Tests for verifying basic functionality of download
 *
 *  - Initiates a download
 *  - Verifies download prompt
 *  - Verifies download notification and actions
 *  - Verifies managing downloads inside the Downloads listing.
 **/
class DownloadTest {
    private lateinit var mDevice: UiDevice
    private val featureSettingsHelper = FeatureSettingsHelper()
    /* Remote test page managed by Mozilla Mobile QA team at https://github.com/mozilla-mobile/testapp */
    private val downloadTestPage = "https://storage.googleapis.com/mobile_test_assets/test_app/downloads.html"
    private var downloadFile: String = ""

    @get:Rule
    val composeTestRule = AndroidComposeTestRule(
        HomeActivityIntentTestRule()
    ) { it.activity }

    @get: Rule
    // Making sure to grant storage access for this test running on API 28
    var watcher: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            if (description.methodName == "pauseResumeCancelDownloadTest") {
                PermissionRequester().apply {
                    addPermissions(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                    requestPermissions()
                }
            }
        }
    }

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // disabling the jump-back-in pop-up that interferes with the tests.
        featureSettingsHelper.setJumpBackCFREnabled(false)
        // disable the TCP CFR that appears when loading webpages and interferes with the tests.
        featureSettingsHelper.setTCPCFREnabled(false)
        // disabling the PWA CFR on 3rd visit
        featureSettingsHelper.disablePwaCFR(true)
        // clear all existing notifications
        notificationShade {
            mDevice.openNotification()
            clearNotifications()
        }
    }

    @After
    fun tearDown() {
        featureSettingsHelper.resetAllFeatureFlags()
        notificationShade {
            cancelAllShownNotifications()
        }
    }

    @Test
    fun testDownloadPrompt() {
        downloadFile = "web_icon.png"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(downloadTestPage.toUri()) {
            waitForPageToLoad()
        }.clickDownloadLink(downloadFile) {
            verifyDownloadPrompt(downloadFile)
        }.clickDownload {
            verifyDownloadNotificationPopup()
        }.clickOpen("image/png") {}

        downloadRobot {
            verifyPhotosAppOpens()
        }

        mDevice.pressBack()
    }

    @Test
    fun testCloseDownloadPrompt() {
        downloadFile = "smallZip.zip"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(downloadTestPage.toUri()) {
            waitForPageToLoad()
        }.clickDownloadLink(downloadFile) {
            verifyDownloadPrompt(downloadFile)
        }.closePrompt {
        }.openThreeDotMenu {
        }.openDownloadsManager {
            verifyEmptyDownloadsList(composeTestRule)
        }
    }

    @Test
    fun testDownloadCompleteNotification() {
        downloadFile = "smallZip.zip"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(downloadTestPage.toUri()) {
            waitForPageToLoad()
        }.clickDownloadLink(downloadFile) {
            verifyDownloadPrompt(downloadFile)
        }.clickDownload {
            verifyDownloadNotificationPopup()
        }.closePrompt {}

        mDevice.openNotification()
        notificationShade {
            verifySystemNotificationExists("Download completed")
        }
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P, codeName = "P")
    @SmokeTest
    @Test
    fun pauseResumeCancelDownloadTest() {
        downloadFile = "1GB.zip"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(downloadTestPage.toUri()) {
            waitForPageToLoad()
        }.clickDownloadLink(downloadFile) {
            verifyDownloadPrompt(downloadFile)
        }.clickDownload {}

        mDevice.openNotification()
        notificationShade {
            verifySystemNotificationExists("Firefox Fenix")
            expandNotificationMessage()
            clickDownloadNotificationControlButton("PAUSE")
            clickDownloadNotificationControlButton("RESUME")
            clickDownloadNotificationControlButton("CANCEL")
            mDevice.pressBack()
        }

        browserScreen {
        }.openThreeDotMenu {
        }.openDownloadsManager {
            verifyEmptyDownloadsList(composeTestRule)
        }
    }

    @SmokeTest
    @Test
        /* Verifies downloads in the Downloads Menu:
          - downloads appear in the list
          - deleting a download from device storage, removes it from the Downloads Menu too
        */
    fun manageDownloadsInDownloadsMenuTest() {
        // a long filename to verify it's correctly displayed on the prompt and in the Downloads menu
        downloadFile = "tAJwqaWjJsXS8AhzSninBMCfIZbHBGgcc001lx5DIdDwIcfEgQ6vE5Gb5VgAled17DFZ2A7ZDOHA0NpQPHXXFt.svg"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(downloadTestPage.toUri()) {
            waitForPageToLoad()
        }.clickDownloadLink(downloadFile) {
            verifyDownloadPrompt(downloadFile)
        }.clickDownload {
            verifyDownloadNotificationPopup()
        }

        browserScreen {
        }.openThreeDotMenu {
        }.openDownloadsManager {
            verifyDownloadsList(composeTestRule)
            verifyDownloadedFileName(downloadFile, composeTestRule)
            verifyDownloadedFileIcon(composeTestRule)
            openDownloadedFile(downloadFile, composeTestRule)
            verifyPhotosAppOpens()
            deleteDownloadFromStorage()
            verifyDownloadsList(composeTestRule)
        }.exitDownloadsManagerToBrowser {
        }.openThreeDotMenu {
        }.openDownloadsManager {
            verifyEmptyDownloadsList(composeTestRule)
        }
    }
}
