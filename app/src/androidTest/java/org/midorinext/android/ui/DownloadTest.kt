/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.helpers.AndroidAssetDispatcher
import org.midorinext.android.helpers.BrowserActivityTestRule
import org.midorinext.android.helpers.RetryTestRule
import org.midorinext.android.helpers.TestAssetHelper
import org.midorinext.android.ui.robots.downloadRobot
import org.midorinext.android.ui.robots.navigationToolbar
import org.midorinext.android.ui.robots.notificationShade

class DownloadTest {
    private lateinit var mockWebServer: MockWebServer

    @get:Rule
    val activityTestRule = BrowserActivityTestRule()

    @Rule
    @JvmField
    val retryTestRule = RetryTestRule(3)

    @Before
    fun setUp() {
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }
    }

    @After
    fun tearDown() {
        runCatching { mockWebServer.close() }
    }

    @Test
    fun cancelFileDownloadTest() {
        val downloadPage = TestAssetHelper.getDownloadAsset(mockWebServer)
        val downloadFileName = "web_icon.png"

        navigationToolbar {
        }.enterUrlAndEnterToBrowser(downloadPage.url) {}

        downloadRobot {
            cancelDownload()
        }

        notificationShade {
            verifyDownloadNotificationDoesNotExist("Download completed", downloadFileName)
        }.closeNotification {}
    }

    @Ignore("Disabled - https://github.com/mozilla-mobile/reference-browser/issues/2130")
    @Test
    fun fileDownloadTest() {
        val downloadPage = TestAssetHelper.getDownloadAsset(mockWebServer)
        val downloadFileName = "web_icon.png"

        navigationToolbar {
        }.enterUrlAndEnterToBrowser(downloadPage.url) {}

        downloadRobot {
            confirmDownload()
        }

        notificationShade {
            verifyDownloadNotificationExist("Download completed", downloadFileName)
        }.closeNotification {}
    }
}
