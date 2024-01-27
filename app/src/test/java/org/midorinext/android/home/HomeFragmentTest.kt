/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.verify
import mozilla.components.browser.menu.view.MenuButton
import mozilla.components.browser.state.state.SearchState
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.feature.top.sites.TopSite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.midorinext.android.HomeActivity
import org.midorinext.android.MidoriApplication
import org.midorinext.android.components.Core
import org.midorinext.android.ext.application
import org.midorinext.android.ext.components
import org.midorinext.android.home.HomeFragment.Companion.AMAZON_SPONSORED_TITLE
import org.midorinext.android.home.HomeFragment.Companion.EBAY_SPONSORED_TITLE
import org.midorinext.android.utils.Settings

class HomeFragmentTest {

    private lateinit var settings: Settings
    private lateinit var context: Context
    private lateinit var core: Core
    private lateinit var homeFragment: HomeFragment

    @Before
    fun setup() {
        settings = mockk(relaxed = true)
        context = mockk(relaxed = true)
        core = mockk(relaxed = true)

        val midoriApplication: MidoriApplication = mockk(relaxed = true)

        homeFragment = spyk(HomeFragment())

        every { context.application } returns midoriApplication
        every { homeFragment.context } answers { context }
        every { context.components.settings } answers { settings }
        every { context.components.core } answers { core }
    }

    @Test
    fun `WHEN getTopSitesConfig is called THEN it returns TopSitesConfig with non-null frecencyConfig`() {
        every { settings.topSitesMaxLimit } returns 10

        val topSitesConfig = homeFragment.getTopSitesConfig()

        assertNotNull(topSitesConfig.frecencyConfig)
    }

    @Test
    fun `GIVEN a topSitesMaxLimit WHEN getTopSitesConfig is called THEN it returns TopSitesConfig with totalSites = topSitesMaxLimit`() {
        val topSitesMaxLimit = 10
        every { settings.topSitesMaxLimit } returns topSitesMaxLimit

        val topSitesConfig = homeFragment.getTopSitesConfig()

        assertEquals(topSitesMaxLimit, topSitesConfig.totalSites)
    }

    @Test
    fun `GIVEN the selected search engine is set to eBay WHEN getTopSitesConfig is called THEN providerFilter filters the eBay provided top sites`() {
        mockkStatic("mozilla.components.browser.state.state.SearchStateKt")
        every { core.store } returns mockk() {
            every { state } returns mockk() {
                every { search } returns mockk()
            }
        }
        every { any<SearchState>().selectedOrDefaultSearchEngine } returns mockk {
            every { name } returns EBAY_SPONSORED_TITLE
        }
        val eBayTopSite = TopSite.Provided(1L, EBAY_SPONSORED_TITLE, "eBay.com", "", "", "", 0L)
        val amazonTopSite = TopSite.Provided(2L, AMAZON_SPONSORED_TITLE, "Amazon.com", "", "", "", 0L)
        val midoriTopSite = TopSite.Provided(3L, "Midori", "astian.org/midori-browser", "", "", "", 0L)
        val providedTopSites = listOf(eBayTopSite, amazonTopSite, midoriTopSite)

        val topSitesConfig = homeFragment.getTopSitesConfig()

        val filteredProvidedSites = providedTopSites.filter {
            topSitesConfig.providerConfig?.providerFilter?.invoke(it) ?: true
        }
        assertTrue(filteredProvidedSites.containsAll(listOf(amazonTopSite, midoriTopSite)))
        assertFalse(filteredProvidedSites.contains(eBayTopSite))
    }

    @Test
    fun `WHEN configuration changed menu is dismissed`() {
        val menuButton: MenuButton = mockk(relaxed = true)
        homeFragment.getMenuButton = { menuButton }
        homeFragment.onConfigurationChanged(mockk(relaxed = true))

        verify(exactly = 1) { menuButton.dismissMenu() }
    }
    @Test
    fun `GIVEN the user is in normal mode WHEN checking if should enable wallpaper THEN return true`() {
        val activity: HomeActivity = mockk {
            every { themeManager.currentTheme.isPrivate } returns false
        }
        every { homeFragment.activity } returns activity

        assertTrue(homeFragment.shouldEnableWallpaper())
    }

    @Test
    fun `GIVEN the user is in private mode WHEN checking if should enable wallpaper THEN return false`() {
        val activity: HomeActivity = mockk {
            every { themeManager.currentTheme.isPrivate } returns true
        }
        every { homeFragment.activity } returns activity

        assertFalse(homeFragment.shouldEnableWallpaper())
    }
}
