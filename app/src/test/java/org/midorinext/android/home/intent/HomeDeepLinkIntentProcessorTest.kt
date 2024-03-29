/* This Source Code Form is subject to the terms of the Mozilla Public
* License, v. 2.0. If a copy of the MPL was not distributed with this
* file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.intent

import android.content.Intent
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.N
import android.os.Build.VERSION_CODES.P
import androidx.core.net.toUri
import androidx.navigation.NavController
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.concept.engine.EngineSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.BrowserDirection
import org.midorinext.android.BuildConfig.DEEP_LINK_SCHEME
import org.midorinext.android.HomeActivity
import org.midorinext.android.NavGraphDirections
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.components.accounts.MidoriFxAEntryPoint
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.settings.SupportUtils
import org.robolectric.annotation.Config

@RunWith(MidoriRobolectricTestRunner::class)
class HomeDeepLinkIntentProcessorTest {
    private lateinit var activity: HomeActivity
    private lateinit var navController: NavController
    private lateinit var out: Intent
    private lateinit var processorHome: HomeDeepLinkIntentProcessor

    @Before
    fun setup() {
        activity = mockk(relaxed = true)
        navController = mockk(relaxed = true)
        out = mockk()
        processorHome = HomeDeepLinkIntentProcessor(activity)
    }

    @Test
    fun `do not process blank intents`() {
        assertFalse(processorHome.process(Intent(), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `return true if scheme is midori`() {
        assertTrue(processorHome.process(testIntent("test"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `return true if scheme is a Midori variant`() {
        assertTrue(processorHome.process(testIntent("midori-beta://test"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `process home deep link`() {
        assertTrue(processorHome.process(testIntent("home"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalHome()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process urls_bookmarks deep link`() {
        assertTrue(processorHome.process(testIntent("urls_bookmarks"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalBookmarkFragment(BookmarkRoot.Root.id)) }
        verify { out wasNot Called }
    }

    @Test
    fun `process urls_history deep link`() {
        assertTrue(processorHome.process(testIntent("urls_history"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalHistoryFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process home_collections deep link`() {
        assertTrue(processorHome.process(testIntent("home_collections"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalHome()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings deep link`() {
        assertTrue(processorHome.process(testIntent("settings"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalSettingsFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process turn_on_sync deep link`() {
        assertTrue(processorHome.process(testIntent("turn_on_sync"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalTurnOnSync(
            entrypoint = MidoriFxAEntryPoint.DeepLink,
        )) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_search_engine deep link`() {
        assertTrue(processorHome.process(testIntent("settings_search_engine"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalSearchEngineFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_accessibility deep link`() {
        assertTrue(processorHome.process(testIntent("settings_accessibility"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalAccessibilityFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_delete_browsing_data deep link`() {
        assertTrue(processorHome.process(testIntent("settings_delete_browsing_data"), navController, out))

        verify { activity wasNot Called }
        verify { navController.navigate(NavGraphDirections.actionGlobalDeleteBrowsingDataFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_addon_manager deep link`() {
        assertTrue(processorHome.process(testIntent("settings_addon_manager"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalAddonsManagementFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_logins deep link`() {
        assertTrue(processorHome.process(testIntent("settings_logins"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalSavedLoginsAuthFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_tracking_protection deep link`() {
        assertTrue(processorHome.process(testIntent("settings_tracking_protection"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalTrackingProtectionFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_privacy deep link`() {
        assertTrue(processorHome.process(testIntent("settings_privacy"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalSettingsFragment()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process enable_private_browsing deep link`() {
        assertTrue(processorHome.process(testIntent("enable_private_browsing"), navController, out))

        verify { activity.browsingModeManager.mode = BrowsingMode.Private }
        verify { navController.navigate(NavGraphDirections.actionGlobalHome()) }
        verify { out wasNot Called }
    }

    @Test
    fun `process open deep link`() {
        assertTrue(processorHome.process(testIntent("open"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }

        assertTrue(processorHome.process(testIntent("open?url=test"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }

        assertTrue(processorHome.process(testIntent("open?url=https%3A%2F%2Fwww.example.org%2F"), navController, out))

        verify {
            activity.openToBrowserAndLoad(
                "https://www.example.org/",
                newTab = true,
                from = BrowserDirection.FromGlobal,
                flags = EngineSession.LoadUrlFlags.external()
            )
        }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `process invalid open deep link`() {
        val invalidProcessor = HomeDeepLinkIntentProcessor(activity)

        assertTrue(invalidProcessor.process(testIntent("open"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }

        assertTrue(invalidProcessor.process(testIntent("open?url=open?url=https%3A%2F%2Fwww.example.org%2F"), navController, out))

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    @Config(minSdk = N, maxSdk = P)
    fun `process make_default_browser deep link for above API 23`() {
        assertTrue(processorHome.process(testIntent("make_default_browser"), navController, out))

        verify { activity.startActivity(any()) }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    @Config(maxSdk = M)
    fun `process make_default_browser deep link for API 23 and below`() {
        assertTrue(processorHome.process(testIntent("make_default_browser"), navController, out))

        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = SupportUtils.getSumoURLForTopic(
                    activity,
                    SupportUtils.SumoTopic.SET_AS_DEFAULT_BROWSER
                ),
                newTab = true,
                from = BrowserDirection.FromGlobal,
                flags = EngineSession.LoadUrlFlags.external()
            )
        }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `process settings_notifications deep link`() {
        assertTrue(processorHome.process(testIntent("settings_notifications"), navController, out))

        verify { navController wasNot Called }
        verify { out wasNot Called }
        verify { activity.startActivity(any()) }
    }

    @Test
    fun `process settings_wallpapers deep link`() {
        assertTrue(processorHome.process(testIntent("settings_wallpapers"), navController, out))

        verify { navController.navigate(NavGraphDirections.actionGlobalWallpaperSettingsFragment()) }
        verify { out wasNot Called }
    }

    private fun testIntent(uri: String) = Intent("", "$DEEP_LINK_SCHEME://$uri".toUri())
}
