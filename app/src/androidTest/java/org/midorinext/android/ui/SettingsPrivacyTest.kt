/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.ui

import android.os.Build
import android.view.autofill.AutofillManager
import androidx.core.net.toUri
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.R
import org.midorinext.android.customannotations.SmokeTest
import org.midorinext.android.ext.settings
import org.midorinext.android.helpers.AndroidAssetDispatcher
import org.midorinext.android.helpers.FeatureSettingsHelper
import org.midorinext.android.helpers.HomeActivityIntentTestRule
import org.midorinext.android.helpers.TestAssetHelper
import org.midorinext.android.helpers.TestAssetHelper.getStorageTestAsset
import org.midorinext.android.helpers.TestHelper
import org.midorinext.android.helpers.TestHelper.appContext
import org.midorinext.android.helpers.TestHelper.exitMenu
import org.midorinext.android.helpers.TestHelper.generateRandomString
import org.midorinext.android.helpers.TestHelper.getStringResource
import org.midorinext.android.helpers.TestHelper.openAppFromExternalLink
import org.midorinext.android.helpers.TestHelper.restartApp
import org.midorinext.android.helpers.TestHelper.setNetworkEnabled
import org.midorinext.android.ui.robots.addToHomeScreen
import org.midorinext.android.ui.robots.browserScreen
import org.midorinext.android.ui.robots.homeScreen
import org.midorinext.android.ui.robots.navigationToolbar
import org.midorinext.android.ui.robots.settingsScreen

/**
 *  Tests for verifying the main three dot menu options
 *
 */

class SettingsPrivacyTest {
    /* ktlint-disable no-blank-line-before-rbrace */ // This imposes unreadable grouping.

    private lateinit var mDevice: UiDevice
    private lateinit var mockWebServer: MockWebServer
    private val pageShortcutName = generateRandomString(5)
    private val featureSettingsHelper = FeatureSettingsHelper()

    @get:Rule
    val activityTestRule = HomeActivityIntentTestRule(skipOnboarding = true)

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }

        featureSettingsHelper.setJumpBackCFREnabled(false)
        featureSettingsHelper.setTCPCFREnabled(false)
        featureSettingsHelper.disablePwaCFR(true)

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            val autofillManager: AutofillManager =
                appContext.getSystemService(AutofillManager::class.java)
            autofillManager.disableAutofillServices()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        featureSettingsHelper.resetAllFeatureFlags()
    }

    @Test
    // Walks through settings privacy menu and sub-menus to ensure all items are present
    fun settingsPrivacyItemsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
            // PRIVACY
            verifyPrivacyHeading()

            // PRIVATE BROWSING
            verifyPrivateBrowsingButton()
        }.openPrivateBrowsingSubMenu {
            verifyNavigationToolBarHeader()
        }.goBack {
            // HTTPS-Only Mode
            verifyHTTPSOnlyModeButton()
            verifyHTTPSOnlyModeState("Off")

            // ENHANCED TRACKING PROTECTION
            verifyEnhancedTrackingProtectionButton()
            verifyEnhancedTrackingProtectionState("On")
        }.openEnhancedTrackingProtectionSubMenu {
            verifyNavigationToolBarHeader()
            verifyEnhancedTrackingProtectionProtectionSubMenuItems()

            // ENHANCED TRACKING PROTECTION EXCEPTION
        }.openExceptions {
            verifyNavigationToolBarHeader()
            verifyEnhancedTrackingProtectionProtectionExceptionsSubMenuItems()
        }.goBack {
        }.goBack {

            // SITE PERMISSIONS
            verifySitePermissionsButton()
        }.openSettingsSubMenuSitePermissions {
            verifyNavigationToolBarHeader()
            verifySitePermissionsSubMenuItems()

            // SITE PERMISSIONS AUTOPLAY
        }.openAutoPlay {
            verifyNavigationToolBarHeader("Autoplay")
            verifySitePermissionsAutoPlaySubMenuItems()
        }.goBack {

            // SITE PERMISSIONS CAMERA
        }.openCamera {
            verifyNavigationToolBarHeader("Camera")
            verifySitePermissionsCommonSubMenuItems()
            verifyToggleNameToON("3. Toggle Camera to ON")
        }.goBack {

            // SITE PERMISSIONS LOCATION
        }.openLocation {
            verifyNavigationToolBarHeader("Location")
            verifySitePermissionsCommonSubMenuItems()
            verifyToggleNameToON("3. Toggle Location to ON")
        }.goBack {

            // SITE PERMISSIONS MICROPHONE
        }.openMicrophone {
            verifyNavigationToolBarHeader("Microphone")
            verifySitePermissionsCommonSubMenuItems()
            verifyToggleNameToON("3. Toggle Microphone to ON")
        }.goBack {

            // SITE PERMISSIONS NOTIFICATION
        }.openNotification {
            verifyNavigationToolBarHeader("Notification")
            verifySitePermissionsNotificationSubMenuItems()
        }.goBack {

            // SITE PERMISSIONS PERSISTENT STORAGE
        }.openPersistentStorage {
            verifyNavigationToolBarHeader("Persistent Storage")
            verifySitePermissionsPersistentStorageSubMenuItems()
        }.goBack {

            // SITE PERMISSIONS EXCEPTIONS
        }.openExceptions {
            verifyNavigationToolBarHeader()
            verifySitePermissionsExceptionSubMenuItems()
        }.goBack {
        }.goBack {

            // DELETE BROWSING DATA
            verifyDeleteBrowsingDataButton()
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyNavigationToolBarHeader()
            verifyDeleteBrowsingDataSubMenuItems()
        }.goBack {

            // DELETE BROWSING DATA ON QUIT
            verifyDeleteBrowsingDataOnQuitButton()
            verifyDeleteBrowsingDataOnQuitState("Off")
        }.openSettingsSubMenuDeleteBrowsingDataOnQuit {
            verifyNavigationToolBarHeader()
            verifyDeleteBrowsingDataOnQuitSubMenuItems()
        }.goBack {

            // NOTIFICATIONS
            verifyNotificationsButton()
        }.openSettingsSubMenuNotifications {
            verifySystemNotificationsView()
        }.goBack {

            // DATA COLLECTION
            verifyDataCollectionButton()
        }.goBack {
            verifyHomeComponent()
        }
    }

    // Tests only for initial state without signing in.
    // For tests after singing in, see SyncIntegration test suite

    @Test
    fun loginsAndPasswordsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
            // Necessary to scroll a little bit for all screen sizes
            TestHelper.scrollToElementByText("Logins and passwords")
        }.openLoginsAndPasswordSubMenu {
            verifyDefaultView()
            verifyDefaultValueAutofillLogins(InstrumentationRegistry.getInstrumentation().targetContext)
            verifyDefaultValueExceptions()
        }.openSavedLogins {
            verifySecurityPromptForLogins()
            tapSetupLater()
            // Verify that logins list is empty
            // Issue #7272 nothing is shown
        }.goBack {
        }.openSyncLogins {
            verifyReadyToScanOption()
            verifyUseEmailOption()
        }
    }

    @Test
    fun saveLoginFromPromptTest() {
        val saveLoginTest =
            TestAssetHelper.getSaveLoginAsset(mockWebServer)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(saveLoginTest.url) {
            verifySaveLoginPromptIsShown()
            // Click save to save the login
            saveLoginFromPrompt("Save")
        }
        browserScreen {
        }.openThreeDotMenu {
        }.openSettings {
            TestHelper.scrollToElementByText("Logins and passwords")
        }.openLoginsAndPasswordSubMenu {
            verifyDefaultView()
        }.openSavedLogins {
            verifySecurityPromptForLogins()
            tapSetupLater()
            // Verify that the login appears correctly
            verifySavedLoginFromPrompt("test@example.com")
        }
    }

    @Test
    fun neverSaveLoginFromPromptTest() {
        val saveLoginTest = TestAssetHelper.getSaveLoginAsset(mockWebServer)
        val settings = activityTestRule.activity.settings()
        settings.shouldShowJumpBackInCFR = false

        navigationToolbar {
        }.enterURLAndEnterToBrowser(saveLoginTest.url) {
            verifySaveLoginPromptIsShown()
            // Don't save the login, add to exceptions
            saveLoginFromPrompt("Never save")
        }.openThreeDotMenu {
        }.openSettings {
        }.openLoginsAndPasswordSubMenu {
            verifyDefaultView()
        }.openSavedLogins {
            verifySecurityPromptForLogins()
            tapSetupLater()
            // Verify that the login list is empty
            verifyNotSavedLoginFromPrompt()
        }.goBack {
        }.openLoginExceptions {
            // Verify localhost was added to exceptions list
            verifyLocalhostExceptionAdded()
        }
    }

    @Test
    fun saveLoginsAndPasswordsOptions() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openLoginsAndPasswordSubMenu {
        }.saveLoginsAndPasswordsOptions {
            verifySaveLoginsOptionsView()
        }
    }

    @SmokeTest
    @Test
    fun openWebsiteForSavedLoginTest() {
        val loginPage = "https://mozilla-mobile.github.io/testapp/loginForm"
        val originWebsite = "mozilla-mobile.github.io"
        val userName = "test"
        val password = "pass"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(loginPage.toUri()) {
            fillAndSubmitLoginCredentials(userName, password)
            saveLoginFromPrompt("Save")
        }.openThreeDotMenu {
        }.openSettings {
        }.openLoginsAndPasswordSubMenu {
        }.openSavedLogins {
            verifySecurityPromptForLogins()
            tapSetupLater()
            viewSavedLoginDetails(userName)
        }.goToSavedWebsite {
            verifyUrl(originWebsite)
        }
    }

    @SmokeTest
    @Test
    fun verifyMultipleLoginsSelectionsTest() {
        val loginPage = "https://mozilla-mobile.github.io/testapp/loginForm"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(loginPage.toUri()) {
            fillAndSubmitLoginCredentials("midorico", "midori")
            saveLoginFromPrompt("Save")
            fillAndSubmitLoginCredentials("midori", "midorico")
            saveLoginFromPrompt("Save")
            clearUserNameLoginCredential()
            clickSuggestedLoginsButton()
            verifySuggestedUserName("midori")
            verifySuggestedUserName("midorico")
            clickLoginSuggestion("midorico")
            verifyPrefilledLoginCredentials("midorico")
        }
    }

    @Test
    fun verifyPrivateBrowsingMenuItemsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openPrivateBrowsingSubMenu {
            verifyAddPrivateBrowsingShortcutButton()
            verifyOpenLinksInPrivateTab()
            verifyOpenLinksInPrivateTabOff()
        }.goBack {
            verifySettingsView()
        }
    }

    @Test
    fun openExternalLinksInPrivateTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        setOpenLinksInPrivateOn()

        openAppFromExternalLink(firstWebPage.url.toString())

        browserScreen {
            verifyUrl(firstWebPage.url.toString())
        }.openTabDrawer {
            verifyPrivateModeSelected()
        }.closeTabDrawer {
        }.goToHomescreen { }

        setOpenLinksInPrivateOff()

        // We need to open a different link, otherwise it will open the same session
        openAppFromExternalLink(secondWebPage.url.toString())

        browserScreen {
            verifyUrl(secondWebPage.url.toString())
        }.openTabDrawer {
            verifyNormalModeSelected()
        }
    }

    @Test
    @Ignore("Failing after compose migration. See: https://github.com/mozilla-mobile/fenix/issues/26087")
    fun launchPageShortcutInPrivateModeTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        setOpenLinksInPrivateOn()

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
        }.openThreeDotMenu {
        }.openAddToHomeScreen {
            addShortcutName(pageShortcutName)
            clickAddShortcutButton()
            clickAddAutomaticallyButton()
            verifyShortcutAdded(pageShortcutName)
        }

        mDevice.waitForIdle()
        // We need to close the existing tab here, to open a different session
        restartApp(activityTestRule)
        browserScreen {
        }.openTabDrawer {
            closeTab()
        }

        addToHomeScreen {
        }.searchAndOpenHomeScreenShortcut(pageShortcutName) {
        }.openTabDrawer {
            verifyPrivateModeSelected()
        }
    }

    @Ignore("Failing with frequent ANR: https://bugzilla.mozilla.org/show_bug.cgi?id=1764605")
    @Test
    fun launchLinksInPrivateToggleOffStateDoesntChangeTest() {
        val settings = activityTestRule.activity.applicationContext.settings()
        settings.shouldShowJumpBackInCFR = false
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        setOpenLinksInPrivateOn()

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
        }.openThreeDotMenu {
            expandMenu()
        }.openAddToHomeScreen {
            addShortcutName(pageShortcutName)
            clickAddShortcutButton()
            clickAddAutomaticallyButton()
        }.openHomeScreenShortcut(pageShortcutName) {
        }.goToHomescreen { }

        setOpenLinksInPrivateOff()
        restartApp(activityTestRule)
        mDevice.waitForIdle()

        addToHomeScreen {
        }.searchAndOpenHomeScreenShortcut(pageShortcutName) {
        }.openTabDrawer {
            verifyNormalModeSelected()
        }.closeTabDrawer {
        }.openThreeDotMenu {
        }.openSettings {
        }.openPrivateBrowsingSubMenu {
            verifyOpenLinksInPrivateTabOff()
        }
    }

    @Test
    fun addPrivateBrowsingShortcut() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openPrivateBrowsingSubMenu {
            cancelPrivateShortcutAddition()
            addPrivateShortcutToHomescreen()
            verifyPrivateBrowsingShortcutIcon()
        }.openPrivateBrowsingShortcut {
            verifySearchView()
        }.openBrowser {
        }.openTabDrawer {
            verifyPrivateModeSelected()
        }
    }

    @SmokeTest
    @Test
    @SdkSuppress(minSdkVersion = 29)
    // Verifies that you can go to System settings and change app's permissions from inside the app
    fun redirectToAppPermissionsSystemSettingsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuSitePermissions {
        }.openCamera {
            verifyBlockedByAndroid()
        }.goBack {
        }.openLocation {
            verifyBlockedByAndroid()
        }.goBack {
        }.openMicrophone {
            verifyBlockedByAndroid()
            clickGoToSettingsButton()
            openAppSystemPermissionsSettings()
            switchAppPermissionSystemSetting("Camera", "Allow")
            goBackToSystemAppPermissionSettings()
            verifySystemGrantedPermission("Camera")
            switchAppPermissionSystemSetting("Location", "Allow")
            goBackToSystemAppPermissionSettings()
            verifySystemGrantedPermission("Location")
            switchAppPermissionSystemSetting("Microphone", "Allow")
            goBackToSystemAppPermissionSettings()
            verifySystemGrantedPermission("Microphone")
            goBackToPermissionsSettingsSubMenu()
            verifyUnblockedByAndroid()
        }.goBack {
        }.openLocation {
            verifyUnblockedByAndroid()
        }.goBack {
        }.openCamera {
            verifyUnblockedByAndroid()
        }
    }

    @Test
    fun deleteBrowsingDataOptionStatesTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyAllCheckBoxesAreChecked()
            switchBrowsingHistoryCheckBox()
            switchCachedFilesCheckBox()
            verifyOpenTabsCheckBox(true)
            verifyBrowsingHistoryDetails(false)
            verifyCookiesCheckBox(true)
            verifyCachedFilesCheckBox(false)
            verifySitePermissionsCheckBox(true)
            verifyDownloadsCheckBox(true)
        }

        restartApp(activityTestRule)

        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyOpenTabsCheckBox(true)
            verifyBrowsingHistoryDetails(false)
            verifyCookiesCheckBox(true)
            verifyCachedFilesCheckBox(false)
            verifySitePermissionsCheckBox(true)
            verifyDownloadsCheckBox(true)
            switchOpenTabsCheckBox()
            switchBrowsingHistoryCheckBox()
            switchCookiesCheckBox()
            switchCachedFilesCheckBox()
            switchSitePermissionsCheckBox()
            switchDownloadsCheckBox()
            verifyOpenTabsCheckBox(false)
            verifyBrowsingHistoryDetails(true)
            verifyCookiesCheckBox(false)
            verifyCachedFilesCheckBox(true)
            verifySitePermissionsCheckBox(false)
            verifyDownloadsCheckBox(false)
        }

        restartApp(activityTestRule)

        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyOpenTabsCheckBox(false)
            verifyBrowsingHistoryDetails(true)
            verifyCookiesCheckBox(false)
            verifyCachedFilesCheckBox(true)
            verifySitePermissionsCheckBox(false)
            verifyDownloadsCheckBox(false)
        }
    }

    @Test
    fun deleteTabsDataWithNoOpenTabsTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyAllCheckBoxesAreChecked()
            selectOnlyOpenTabsCheckBox()
            clickDeleteBrowsingDataButton()
            confirmDeletionAndAssertSnackbar()
        }
        settingsScreen {
            verifyGeneralHeading()
        }
    }

    @SmokeTest
    @Test
    fun deleteTabsDataTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
            mDevice.waitForIdle()
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyAllCheckBoxesAreChecked()
            selectOnlyOpenTabsCheckBox()
            clickDeleteBrowsingDataButton()
            clickDialogCancelButton()
            verifyOpenTabsCheckBox(true)
            clickDeleteBrowsingDataButton()
            confirmDeletionAndAssertSnackbar()
        }
        settingsScreen {
            verifyGeneralHeading()
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyOpenTabsDetails("0")
        }.goBack {
        }.goBack {
        }.openTabDrawer {
            verifyNoOpenTabsInNormalBrowsing()
        }
    }

    @SmokeTest
    @Test
    fun deleteBrowsingHistoryAndSiteDataTest() {
        val storageWritePage = getStorageTestAsset(mockWebServer, "storage_write.html").url
        val storageCheckPage = getStorageTestAsset(mockWebServer, "storage_check.html").url

        navigationToolbar {
        }.enterURLAndEnterToBrowser(storageWritePage) {
        }.openNavigationToolbar {
        }.enterURLAndEnterToBrowser(storageCheckPage) {
            verifyPageContent("Session storage has value")
            verifyPageContent("Local storage has value")
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            verifyBrowsingHistoryDetails("2")
            selectOnlyBrowsingHistoryCheckBox()
            clickDeleteBrowsingDataButton()
            clickDialogCancelButton()
            verifyBrowsingHistoryDetails(true)
            clickDeleteBrowsingDataButton()
            confirmDeletionAndAssertSnackbar()
            verifyBrowsingHistoryDetails("0")
            exitMenu()
        }
        navigationToolbar {
        }.openThreeDotMenu {
        }.openHistory {
            // TODO: [Midori] fix this
//            verifyEmptyHistoryView()
            mDevice.pressBack()
        }
        navigationToolbar {
        }.enterURLAndEnterToBrowser(storageCheckPage) {
            verifyPageContent("Session storage empty")
            verifyPageContent("Local storage empty")
        }
    }

    @SmokeTest
    @Test
    fun deleteCookiesTest() {
        val cookiesTestPage = getStorageTestAsset(mockWebServer, "storage_write.html").url

        navigationToolbar {
        }.enterURLAndEnterToBrowser(cookiesTestPage) {
            verifyPageContent("No cookies set")
            clickSetCookiesButton()
            verifyPageContent("user=android")
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            selectOnlyCookiesCheckBox()
            clickDeleteBrowsingDataButton()
            confirmDeletionAndAssertSnackbar()
            exitMenu()
        }
        browserScreen {
        }.openThreeDotMenu {
        }.refreshPage {
            verifyPageContent("No cookies set")
        }
    }

    @SmokeTest
    @Test
    fun deleteCachedFilesTest() {
        homeScreen {
            verifyExistingTopSitesTabs("Wikipedia")
        }.openTopSiteTabWithTitle("Wikipedia") {
            waitForPageToLoad()
        }.openTabDrawer {
        }.openNewTab {
        }.submitQuery("about:cache") {
            // disabling wifi to prevent downloads in the background
            setNetworkEnabled(enabled = false)
        }.openThreeDotMenu {
        }.openSettings {
        }.openSettingsSubMenuDeleteBrowsingData {
            selectOnlyCachedFilesCheckBox()
            clickDeleteBrowsingDataButton()
            confirmDeletionAndAssertSnackbar()
            exitMenu()
        }
        browserScreen {
        }.openThreeDotMenu {
        }.refreshPage {
            verifyNetworkCacheIsEmpty("memory")
            verifyNetworkCacheIsEmpty("disk")
        }
        setNetworkEnabled(enabled = true)
    }

    @SmokeTest
    @Test
    fun saveLoginsInPWATest() {
        val pwaPage = "https://mozilla-mobile.github.io/testapp/loginForm"
        val shortcutTitle = "TEST_APP"

        navigationToolbar {
        }.enterURLAndEnterToBrowser(pwaPage.toUri()) {
            verifyNotificationDotOnMainMenu()
        }.openThreeDotMenu {
        }.clickInstall {
            clickAddAutomaticallyButton()
        }.openHomeScreenShortcut(shortcutTitle) {
            mDevice.waitForIdle()
            fillAndSubmitLoginCredentials("midorico", "midori")
            verifySaveLoginPromptIsDisplayed()
            saveLoginFromPrompt("Save")
            openAppFromExternalLink(pwaPage)

            browserScreen {
            }.openThreeDotMenu {
            }.openSettings {
            }.openLoginsAndPasswordSubMenu {
            }.openSavedLogins {
                verifySecurityPromptForLogins()
                tapSetupLater()
                verifySavedLoginFromPrompt("midorico")
            }

            addToHomeScreen {
            }.searchAndOpenHomeScreenShortcut(shortcutTitle) {
                verifyPrefilledPWALoginCredentials("midorico", shortcutTitle)
            }
        }
    }
}

private fun setOpenLinksInPrivateOn() {
    homeScreen {
    }.openThreeDotMenu {
    }.openSettings {
    }.openPrivateBrowsingSubMenu {
        verifyOpenLinksInPrivateTabEnabled()
        clickOpenLinksInPrivateTabSwitch()
    }.goBack {
    }.goBack {
        verifyHomeComponent()
    }
}

private fun setOpenLinksInPrivateOff() {
    homeScreen {
    }.openThreeDotMenu {
    }.openSettings {
    }.openPrivateBrowsingSubMenu {
        clickOpenLinksInPrivateTabSwitch()
        verifyOpenLinksInPrivateTabOff()
    }.goBack {
    }.goBack {
        verifyHomeComponent()
    }
}
