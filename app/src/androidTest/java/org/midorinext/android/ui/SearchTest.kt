/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.net.toUri
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.filters.SdkSuppress
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.icons.generator.DefaultIconGenerator
import mozilla.components.feature.search.ext.createSearchEngine
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.customannotations.SmokeTest
import org.midorinext.android.helpers.Constants.PackageName.ANDROID_SETTINGS
import org.midorinext.android.helpers.FeatureSettingsHelper
import org.midorinext.android.helpers.HomeActivityTestRule
import org.midorinext.android.helpers.SearchDispatcher
import org.midorinext.android.helpers.TestHelper.appContext
import org.midorinext.android.helpers.TestHelper.assertNativeAppOpens
import org.midorinext.android.helpers.TestHelper.denyPermission
import org.midorinext.android.helpers.TestHelper.exitMenu
import org.midorinext.android.helpers.TestHelper.grantPermission
import org.midorinext.android.helpers.TestHelper.longTapSelectItem
import org.midorinext.android.helpers.TestHelper.setCustomSearchEngine
import org.midorinext.android.ui.robots.browserScreen
import org.midorinext.android.ui.robots.homeScreen
import org.midorinext.android.ui.robots.multipleSelectionToolbar

/**
 *  Tests for verifying the search fragment
 *
 *  Including:
 * - Verify the toolbar, awesomebar, and shortcut bar are displayed
 * - Select shortcut button
 * - Select scan button
 *
 */

class SearchTest {
    private val featureSettingsHelper = FeatureSettingsHelper()
    lateinit var searchMockServer: MockWebServer

    @get:Rule
    val activityTestRule = AndroidComposeTestRule(
        HomeActivityTestRule(),
        { it.activity }
    )

    @Before
    fun setUp() {
        searchMockServer = MockWebServer().apply {
            dispatcher = SearchDispatcher()
            start()
        }
        featureSettingsHelper.setJumpBackCFREnabled(false)
        featureSettingsHelper.setTCPCFREnabled(false)
    }

    @After
    fun tearDown() {
        searchMockServer.shutdown()
        featureSettingsHelper.resetAllFeatureFlags()
    }

    @Test
    fun searchScreenItemsTest() {
        homeScreen {
        }.openSearch {
            verifySearchView()
            verifyBrowserToolbar()
            verifyScanButton()
            verifySearchEngineButton()
        }
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P, codeName = "P")
    @SmokeTest
    @Test
    fun scanButtonDenyPermissionTest() {
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        assumeTrue(cameraManager.cameraIdList.isNotEmpty())

        homeScreen {
        }.openSearch {
            clickScanButton()
            denyPermission()
            clickScanButton()
            clickDismissPermissionRequiredDialog()
        }
        homeScreen {
        }.openSearch {
            clickScanButton()
            clickGoToPermissionsSettings()
            assertNativeAppOpens(ANDROID_SETTINGS)
        }
    }

    @SdkSuppress(maxSdkVersion = Build.VERSION_CODES.P, codeName = "P")
    @SmokeTest
    @Test
    fun scanButtonAllowPermissionTest() {
        val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        assumeTrue(cameraManager.cameraIdList.isNotEmpty())

        homeScreen {
        }.openSearch {
            clickScanButton()
            grantPermission()
            verifyScannerOpen()
        }
    }

    @Test
    fun shortcutButtonTest() {
        val searchEngineURL = "bing.com/search?q=midorico%20midori"

        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSearchSubMenu {
            enableShowSearchShortcuts()
        }.goBack {
        }.goBack {
        }.openSearch {
            verifySearchBarEmpty()
            clickSearchEngineButton(activityTestRule, "Bing")
            typeSearch("midorico")
            verifySearchEngineResults(activityTestRule, "midorico midori", "Bing")
            clickSearchEngineResult(activityTestRule, "midorico midori")
        }

        browserScreen {
            waitForPageToLoad()
            verifyUrl(searchEngineURL)
        }
    }

    @Test
    fun shortcutSearchEngineSettingsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSearchSubMenu {
            enableShowSearchShortcuts()
        }.goBack {
        }.goBack {
        }.openSearch {
            scrollToSearchEngineSettings(activityTestRule)
            clickSearchEngineSettings(activityTestRule)
            verifySearchSettings()
        }
    }

    @Test
    fun clearSearchTest() {
        homeScreen {
        }.openSearch {
            typeSearch("test")
            clickClearButton()
            verifySearchBarEmpty()
        }
    }

    @Ignore("Failure caused by bugs: https://github.com/mozilla-mobile/fenix/issues/23818")
    @SmokeTest
    @Test
    fun searchGroupShowsInRecentlyVisitedTest() {
        val firstPage = searchMockServer.url("generic1.html").toString()
        val secondPage = searchMockServer.url("generic2.html").toString()
        // setting our custom mockWebServer search URL
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.openSearch {
        }.submitQuery("test search") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInNewTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInNewTab()
        }.goToHomescreen {
            verifyJumpBackInSectionIsDisplayed()
            verifyCurrentSearchGroupIsDisplayed(true, "test search", 3)
            verifyRecentlyVisitedSearchGroupDisplayed(false, "test search", 3)
        }.openTabDrawer {
        }.openTabFromGroup(firstPage) {
        }.openTabDrawer {
        }.openTabFromGroup(secondPage) {
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            verifyRecentlyVisitedSearchGroupDisplayed(true, "test search", 3)
        }
    }

    @SmokeTest
    @Test
    fun noCurrentSearchGroupFromPrivateBrowsingTest() {
        // setting our custom mockWebServer search URL
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.openSearch {
        }.submitQuery("test search") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInPrivateTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInPrivateTab()
        }.goToHomescreen {
            verifyCurrentSearchGroupIsDisplayed(false, "test search", 3)
        }.openThreeDotMenu {
        }.openHistory {
            verifyHistoryItemExists(false, "3 sites", activityTestRule)
        }
    }

    @SmokeTest
    @Test
    @Ignore("Failing after compose migration. See: https://github.com/mozilla-mobile/fenix/issues/26087")
    fun noRecentlyVisitedSearchGroupInPrivateBrowsingTest() {
        val firstPage = searchMockServer.url("generic1.html").toString()
        val secondPage = searchMockServer.url("generic2.html").toString()
        // setting our custom mockWebServer search URL
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.togglePrivateBrowsingMode()
        homeScreen {
        }.openSearch {
        }.submitQuery("test search") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInPrivateTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInPrivateTab()
        }.openTabDrawer {
        }.openTab(firstPage) {
        }.openTabDrawer {
        }.openTab(secondPage) {
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            homeScreen {
            }.togglePrivateBrowsingMode()
            verifyRecentlyVisitedSearchGroupDisplayed(false, "test search", 3)
        }
    }

    @Ignore("Failure caused by bugs: https://github.com/mozilla-mobile/fenix/issues/23818")
    @SmokeTest
    @Test
    fun deleteItemsFromSearchGroupsHistoryTest() {
        val firstPage = searchMockServer.url("generic1.html").toString()
        val secondPage = searchMockServer.url("generic2.html").toString()
        // setting our custom mockWebServer search URL
        val searchString = "http://localhost:${searchMockServer.port}/searchResults.html?search={searchTerms}"
        val customSearchEngine = createSearchEngine(
            name = "TestSearchEngine",
            url = searchString,
            icon = DefaultIconGenerator().generate(appContext, IconRequest(searchString)).bitmap
        )
        setCustomSearchEngine(customSearchEngine)

        // Performs a search and opens 2 dummy search results links to create a search group
        homeScreen {
        }.openSearch {
        }.submitQuery("test search") {
            longClickMatchingText("Link 1")
            clickContextOpenLinkInNewTab()
            longClickMatchingText("Link 2")
            clickContextOpenLinkInNewTab()
        }.openTabDrawer {
        }.openTabFromGroup(firstPage) {
        }.openTabDrawer {
        }.openTabFromGroup(secondPage) {
        }.openTabDrawer {
        }.openTabsListThreeDotMenu {
        }.closeAllTabs {
            verifyRecentlyVisitedSearchGroupDisplayed(true, "test search", 3)
        }.openRecentlyVisitedSearchGroupHistoryList("test search") {
            clickDeleteHistoryButton(firstPage, activityTestRule)
            longTapSelectItem(secondPage.toUri())
            multipleSelectionToolbar {
                openActionBarOverflowOrOptionsMenu(activityTestRule.activity)
                clickMultiSelectionDelete()
            }
            exitMenu()
        }
        homeScreen {
            // checking that the group is removed when only 1 item is left
            verifyRecentlyVisitedSearchGroupDisplayed(false, "test search", 1)
        }
    }
}
