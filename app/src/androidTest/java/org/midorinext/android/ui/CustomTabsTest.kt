/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.midorinext.android.ui

import androidx.test.rule.ActivityTestRule
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.IntentReceiverActivity
import org.midorinext.android.helpers.AndroidAssetDispatcher
import org.midorinext.android.helpers.BrowserActivityTestRule
import org.midorinext.android.helpers.RetryTestRule
import org.midorinext.android.helpers.TestAssetHelper
import org.midorinext.android.helpers.TestHelper.createCustomTabIntent
import org.midorinext.android.ui.robots.customTabScreen

class CustomTabsTest {
    private lateinit var mockWebServer: MockWebServer

    @get:Rule
    val activityTestRule = BrowserActivityTestRule()

    @get:Rule
    val intentReceiverActivityTestRule = ActivityTestRule(
        IntentReceiverActivity::class.java,
        true,
        false,
    )

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
    fun openCustomTabTest() {
        val customTabPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                customTabPage.url.toString(),
            ),
        )

        customTabScreen {
            verifyCloseButton()
            verifyTrackingProtectionIcon()
            verifySecurityIndicator()
            verifyPageTitle(customTabPage.title)
            verifyPageUrl(customTabPage.url.toString())
            verifyActionButton()
            verifyMenuButton()
        }
    }

    @Test
    fun verifyCustomTabMenuItemsTest() {
        val customTabPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                customTabPage.url.toString(),
            ),
        )

        customTabScreen {
        }.openMainMenu {
            verifyForwardButton()
            verifyRefreshButton()
            verifyStopButton()
            verifyShareButton()
            verifyRequestDesktopButton()
            verifyFindInPageButton()
            verifyOpenInBrowserButton()
        }
    }

    @Test
    fun customTabNavigationTest() {
        val pageLinks = TestAssetHelper.getGenericAsset(mockWebServer, 4)
        val genericURL = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                pageLinks.url.toString(),
            ),
        )

        customTabScreen {
            clickGenericLink("Link 1")
            verifyPageTitle(genericURL.title)
            verifyPageUrl(genericURL.url.toString())
        }.goBack {
            verifyPageTitle(pageLinks.title)
            verifyPageUrl(pageLinks.url.toString())
        }.openMainMenu {
            clickForwardButton()
            verifyPageTitle(genericURL.title)
            verifyPageUrl(genericURL.url.toString())
        }
    }

    @Test
    fun customTabShareTest() {
        val customTabPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                customTabPage.url.toString(),
            ),
        )

        customTabScreen {
        }.openMainMenu {
        }.clickShareButton {
            verifyShareContentPanel()
        }
    }

    @Test
    fun customTabRequestDesktopSiteTest() {
        val customTabPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                customTabPage.url.toString(),
            ),
        )

        customTabScreen {
        }.openMainMenu {
            switchRequestDesktopSiteToggle()
        }.openMainMenu {
            verifyRequestDesktopSiteIsTurnedOn()
            switchRequestDesktopSiteToggle()
        }.openMainMenu {
            verifyRequestDesktopSiteIsTurnedOff()
        }
    }

    @Test
    fun customTabOpenInBrowserTest() {
        val customTabPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        intentReceiverActivityTestRule.launchActivity(
            createCustomTabIntent(
                customTabPage.url.toString(),
            ),
        )

        customTabScreen {
        }.openMainMenu {
        }.clickOpenInBrowserButton {
            verifyUrl(customTabPage.url.toString())
        }
    }
}
