/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components.toolbar

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.search.SearchUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.top.sites.TopSitesUseCases
import mozilla.components.support.test.ext.joinBlocking
import mozilla.components.support.test.libstate.ext.waitUntilIdle
import mozilla.components.support.test.middleware.CaptureActionsMiddleware
import mozilla.components.ui.tabcounter.TabCounterMenu
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.HomeActivity
import org.midorinext.android.R
import org.midorinext.android.browser.BrowserAnimator
import org.midorinext.android.browser.BrowserFragmentDirections
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.browser.browsingmode.SimpleBrowsingModeManager
import org.midorinext.android.browser.readermode.ReaderModeController
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.home.HomeScreenViewModel

@RunWith(MidoriRobolectricTestRunner::class)
class DefaultBrowserToolbarControllerTest {

    @RelaxedMockK
    private lateinit var activity: HomeActivity

    @MockK(relaxUnitFun = true)
    private lateinit var navController: NavController

    private var tabCounterClicked = false

    @MockK(relaxUnitFun = true)
    private lateinit var engineView: EngineView

    @RelaxedMockK
    private lateinit var searchUseCases: SearchUseCases

    @RelaxedMockK
    private lateinit var sessionUseCases: SessionUseCases

    @RelaxedMockK
    private lateinit var tabsUseCases: TabsUseCases

    @RelaxedMockK
    private lateinit var browserAnimator: BrowserAnimator

    @RelaxedMockK
    private lateinit var topSitesUseCase: TopSitesUseCases

    @RelaxedMockK
    private lateinit var readerModeController: ReaderModeController

    @RelaxedMockK
    private lateinit var homeViewModel: HomeScreenViewModel

    private lateinit var store: BrowserStore
    private val captureMiddleware = CaptureActionsMiddleware<BrowserState, BrowserAction>()

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { activity.components.useCases.sessionUseCases } returns sessionUseCases
        every { activity.components.useCases.searchUseCases } returns searchUseCases
        every { activity.components.useCases.topSitesUseCase } returns topSitesUseCase
        every { navController.currentDestination } returns mockk {
            every { id } returns R.id.browserFragment
        }

        every {
            browserAnimator.captureEngineViewAndDrawStatically(any(), any())
        } answers {
            secondArg<(Boolean) -> Unit>()(true)
        }

        tabCounterClicked = false

        store = BrowserStore(
            initialState = BrowserState(
                tabs = listOf(
                    createTab("https://www.mozilla.org", id = "1")
                ),
                selectedTabId = "1"
            ),
            middleware = listOf(captureMiddleware)
        )
    }

    @After
    fun tearDown() {
        captureMiddleware.reset()
    }

    @Test
    fun handleBrowserToolbarPaste() {
        val pastedText = "Mozilla"
        val controller = createController()
        controller.handleToolbarPaste(pastedText)

        val directions = BrowserFragmentDirections.actionGlobalSearchDialog(
            sessionId = "1",
            pastedText = pastedText
        )

        verify { navController.navigate(directions, any<NavOptions>()) }
    }

    @Test
    fun handleBrowserToolbarPaste_useNewSearchExperience() {
        val pastedText = "Mozilla"
        val controller = createController()
        controller.handleToolbarPaste(pastedText)

        val directions = BrowserFragmentDirections.actionGlobalSearchDialog(
            sessionId = "1",
            pastedText = pastedText
        )

        verify { navController.navigate(directions, any<NavOptions>()) }
    }

    @Test
    fun handleBrowserToolbarPasteAndGoSearch() {
        val pastedText = "Mozilla"

        val controller = createController()
        controller.handleToolbarPasteAndGo(pastedText)

        verify {
            searchUseCases.defaultSearch.invoke(pastedText, "1")
        }

        store.waitUntilIdle()

        captureMiddleware.assertFirstAction(ContentAction.UpdateSearchTermsAction::class) { action ->
            assertEquals("1", action.sessionId)
            assertEquals(pastedText, action.searchTerms)
        }
    }

    @Test
    fun handleBrowserToolbarPasteAndGoUrl() {
        val pastedText = "https://mozilla.org"

        val controller = createController()
        controller.handleToolbarPasteAndGo(pastedText)

        verify {
            sessionUseCases.loadUrl(pastedText)
        }

        store.waitUntilIdle()

        captureMiddleware.assertFirstAction(ContentAction.UpdateSearchTermsAction::class) { action ->
            assertEquals("1", action.sessionId)
            assertEquals("", action.searchTerms)
        }
    }

    @Test
    fun handleTabCounterClick() {
        assertFalse(tabCounterClicked)

        val controller = createController()
        controller.handleTabCounterClick()

        assertTrue(tabCounterClicked)
    }

    @Test
    fun `handle reader mode enabled`() {
        val controller = createController()

        controller.handleReaderModePressed(enabled = true)

        verify { readerModeController.showReaderView() }
    }

    @Test
    fun `handle reader mode disabled`() {
        val controller = createController()

        controller.handleReaderModePressed(enabled = false)

        verify { readerModeController.hideReaderView() }
    }

    @Test
    fun handleToolbarClick() {
        val controller = createController()

        controller.handleToolbarClick()

        val homeDirections = BrowserFragmentDirections.actionGlobalHome()
        val searchDialogDirections = BrowserFragmentDirections.actionGlobalSearchDialog(
            sessionId = "1"
        )

        verify {
            // shows the home screen "behind" the search dialog
            navController.navigate(homeDirections)
            navController.navigate(searchDialogDirections, any<NavOptions>())
        }
    }

    @Test
    fun handleToolbackClickWithSearchTerms() {
        val searchResultsTab = createTab("https://google.com?q=mozilla+website", searchTerms = "mozilla website")
        store.dispatch(TabListAction.AddTabAction(searchResultsTab, select = true)).joinBlocking()

        val controller = createController()
        controller.handleToolbarClick()

        val homeDirections = BrowserFragmentDirections.actionGlobalHome()
        val searchDialogDirections = BrowserFragmentDirections.actionGlobalSearchDialog(
            sessionId = searchResultsTab.id
        )

        // Does not show the home screen "behind" the search dialog if the current session has search terms.
        verify(exactly = 0) {
            navController.navigate(homeDirections)
        }
        verify {
            navController.navigate(searchDialogDirections, any<NavOptions>())
        }
    }

    @Test
    fun handleToolbarCloseTabPressWithLastPrivateSession() {
        val item = TabCounterMenu.Item.CloseTab

        val controller = createController()
        controller.handleTabCounterItemInteraction(item)
        verify {
            homeViewModel.sessionToDelete = "1"
            navController.navigate(BrowserFragmentDirections.actionGlobalHome())
        }
    }

    @Test
    fun handleToolbarCloseTabPress() {
        val item = TabCounterMenu.Item.CloseTab

        val testTab = createTab("https://astian.org")
        store.dispatch(TabListAction.AddTabAction(testTab)).joinBlocking()
        store.dispatch(TabListAction.SelectTabAction(testTab.id)).joinBlocking()

        val controller = createController()
        controller.handleTabCounterItemInteraction(item)
        verify { tabsUseCases.removeTab(testTab.id, selectParentIfExists = true) }
    }

    @Test
    fun handleToolbarNewTabPress() {
        val browsingModeManager = SimpleBrowsingModeManager(BrowsingMode.Private)
        val item = TabCounterMenu.Item.NewTab

        every { activity.browsingModeManager } returns browsingModeManager
        every { navController.navigate(BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)) } just Runs

        val controller = createController()
        controller.handleTabCounterItemInteraction(item)
        assertEquals(BrowsingMode.Normal, browsingModeManager.mode)
        verify { navController.navigate(BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)) }
    }

    @Test
    fun handleToolbarNewPrivateTabPress() {
        val browsingModeManager = SimpleBrowsingModeManager(BrowsingMode.Normal)
        val item = TabCounterMenu.Item.NewPrivateTab

        every { activity.browsingModeManager } returns browsingModeManager
        every { navController.navigate(BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)) } just Runs

        val controller = createController()
        controller.handleTabCounterItemInteraction(item)
        assertEquals(BrowsingMode.Private, browsingModeManager.mode)
        verify { navController.navigate(BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)) }
    }

    @Test
    fun `handleScroll for dynamic toolbars`() {
        val controller = createController()
        every { activity.settings().isDynamicToolbarEnabled } returns true

        controller.handleScroll(10)
        verify { engineView.setVerticalClipping(10) }
    }

    @Test
    fun `handleScroll for static toolbars`() {
        val controller = createController()
        every { activity.settings().isDynamicToolbarEnabled } returns false

        controller.handleScroll(10)
        verify(exactly = 0) { engineView.setVerticalClipping(10) }
    }

    @Test
    fun handleHomeButtonClick() {
        val controller = createController()
        controller.handleHomeButtonClick()

        verify { navController.navigate(BrowserFragmentDirections.actionGlobalHome()) }
    }

    private fun createController(
        activity: HomeActivity = this.activity,
        customTabSessionId: String? = null
    ) = DefaultBrowserToolbarController(
        store = store,
        tabsUseCases = tabsUseCases,
        activity = activity,
        navController = navController,
        engineView = engineView,
        homeViewModel = homeViewModel,
        customTabSessionId = customTabSessionId,
        readerModeController = readerModeController,
        browserAnimator = browserAnimator,
        onTabCounterClicked = {
            tabCounterClicked = true
        },
        onCloseTab = {}
    )
}
