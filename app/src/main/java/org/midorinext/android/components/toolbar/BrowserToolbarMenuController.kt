/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components.toolbar

import android.content.Intent
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.navigation.NavController
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.appservices.places.BookmarkRoot
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession.LoadUrlFlags
import mozilla.components.concept.engine.prompt.ShareData
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.PinnedSiteStorage
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.midorinext.android.HomeActivity
import org.midorinext.android.NavGraphDirections
import org.midorinext.android.R
import org.midorinext.android.browser.BrowserAnimator
import org.midorinext.android.browser.BrowserFragmentDirections
import org.midorinext.android.browser.readermode.ReaderModeController
import org.midorinext.android.collections.SaveCollectionStep
import org.midorinext.android.components.TabCollectionStorage
import org.midorinext.android.components.MidoriSnackbar
import org.midorinext.android.components.accounts.AccountState
import org.midorinext.android.components.accounts.MidoriFxAEntryPoint
import org.midorinext.android.ext.*
import org.midorinext.android.settings.deletebrowsingdata.deleteAndQuit
import org.midorinext.android.utils.Do
import org.midorinext.android.utils.Settings

/**
 * An interface that handles events from the BrowserToolbar menu, triggered by the Interactor
 */
interface BrowserToolbarMenuController {
    fun handleToolbarItemInteraction(item: ToolbarMenu.Item)
}

@Suppress("LargeClass", "ForbiddenComment")
class DefaultBrowserToolbarMenuController(
    private val store: BrowserStore,
    private val activity: HomeActivity,
    private val navController: NavController,
    private val settings: Settings,
    private val readerModeController: ReaderModeController,
    private val sessionFeature: ViewBoundFeatureWrapper<SessionFeature>,
    private val findInPageLauncher: () -> Unit,
    private val browserAnimator: BrowserAnimator,
    private val swipeRefresh: SwipeRefreshLayout,
    private val customTabSessionId: String?,
    private val openInMidoriIntent: Intent,
    private val bookmarkTapped: (String, String) -> Unit,
    private val scope: CoroutineScope,
    private val tabCollectionStorage: TabCollectionStorage,
    private val topSitesStorage: DefaultTopSitesStorage,
    private val pinnedSiteStorage: PinnedSiteStorage,
    private val browserStore: BrowserStore
) : BrowserToolbarMenuController {

    private val currentSession
        get() = store.state.findCustomTabOrSelectedTab(customTabSessionId)

    // We hold onto a reference of the inner scope so that we can override this with the
    // TestCoroutineScope to ensure sequential execution. If we didn't have this, our tests
    // would fail intermittently due to the async nature of coroutine scheduling.
    @VisibleForTesting
    internal var ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    @Suppress("ComplexMethod", "LongMethod")
    override fun handleToolbarItemInteraction(item: ToolbarMenu.Item) {
        val sessionUseCases = activity.components.useCases.sessionUseCases
        val customTabUseCases = activity.components.useCases.customTabsUseCases

        Do exhaustive when (item) {
            // TODO: These can be removed for https://github.com/mozilla-mobile/fenix/issues/17870
            // todo === Start ===
            is ToolbarMenu.Item.InstallPwaToHomeScreen -> {
                settings.installPwaOpened = true
                MainScope().launch {
                    with(activity.components.useCases.webAppUseCases) {
                        if (isInstallable()) {
                            addToHomescreen()
                        } else {
                            val directions =
                                BrowserFragmentDirections.actionBrowserFragmentToCreateShortcutFragment()
                            navController.navigateSafe(R.id.browserFragment, directions)
                        }
                    }
                }
            }
            is ToolbarMenu.Item.OpenInMidori -> {
                customTabSessionId?.let {
                    // Stop the SessionFeature from updating the EngineView and let it release the session
                    // from the EngineView so that it can immediately be rendered by a different view once
                    // we switch to the actual browser.
                    sessionFeature.get()?.release()

                    // Turn this Session into a regular tab and then select it
                    customTabUseCases.migrate(customTabSessionId, select = true)

                    // Switch to the actual browser which should now display our new selected session
                    activity.startActivity(
                        openInMidoriIntent.apply {
                            // We never want to launch the browser in the same task as the external app
                            // activity. So we force a new task here. IntentReceiverActivity will do the
                            // right thing and take care of routing to an already existing browser and avoid
                            // cloning a new one.
                            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )

                    // Close this activity (and the task) since it is no longer displaying any session
                    activity.finishAndRemoveTask()
                }
            }
            // todo === End ===
            is ToolbarMenu.Item.OpenInApp -> {
                settings.openInAppOpened = true

                val appLinksUseCases = activity.components.useCases.appLinksUseCases
                val getRedirect = appLinksUseCases.appLinkRedirect
                currentSession?.let {
                    val redirect = getRedirect.invoke(it.content.url)
                    redirect.appIntent?.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    appLinksUseCases.openAppLink.invoke(redirect.appIntent)
                }
            }
            is ToolbarMenu.Item.Quit -> {
                if (activity.settings().shouldDeleteBrowsingDataOnQuit) {
                    // We need to show the snackbar while the browsing data is deleting (if "Delete
                    // browsing data on quit" is activated). After the deletion is over, the snackbar
                    // is dismissed.
                    val snackbar: MidoriSnackbar? = activity.getRootView()?.let { v ->
                        MidoriSnackbar.make(
                            view = v,
                            duration = Snackbar.LENGTH_LONG,
                            isDisplayedWithBrowserToolbar = true
                        )
                            .setText(v.context.getString(R.string.deleting_browsing_data_in_progress))
                    }

                    deleteAndQuit(activity, scope, snackbar)
                } else {
                    activity.finishAndRemoveTask()
                }
            }
            is ToolbarMenu.Item.CustomizeReaderView -> {
                readerModeController.showControls()
            }
            is ToolbarMenu.Item.Back -> {
                if (item.viewHistory) {
                    navController.navigate(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId
                        )
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goBack.invoke(it.id)
                    }
                }
            }
            is ToolbarMenu.Item.Forward -> {
                if (item.viewHistory) {
                    navController.navigate(
                        BrowserFragmentDirections.actionGlobalTabHistoryDialogFragment(
                            activeSessionId = customTabSessionId
                        )
                    )
                } else {
                    currentSession?.let {
                        sessionUseCases.goForward.invoke(it.id)
                    }
                }
            }
            is ToolbarMenu.Item.Reload -> {
                val flags = if (item.bypassCache) {
                    LoadUrlFlags.select(LoadUrlFlags.BYPASS_CACHE)
                } else {
                    LoadUrlFlags.none()
                }

                currentSession?.let {
                    sessionUseCases.reload.invoke(it.id, flags = flags)
                }
            }
            is ToolbarMenu.Item.Stop -> {
                currentSession?.let {
                    sessionUseCases.stopLoading.invoke(it.id)
                }
            }
            is ToolbarMenu.Item.Share -> {
                val directions = NavGraphDirections.actionGlobalShareFragment(
                    data = arrayOf(
                        ShareData(
                            url = getProperUrl(currentSession),
                            title = currentSession?.content?.title
                        )
                    ),
                    showPage = true
                )
                navController.navigate(directions)
            }
            is ToolbarMenu.Item.Settings -> browserAnimator.captureEngineViewAndDrawStatically {
                val directions = BrowserFragmentDirections.actionBrowserFragmentToSettingsFragment()
                navController.nav(R.id.browserFragment, directions)
            }
            is ToolbarMenu.Item.SyncAccount -> {
                val directions = when (item.accountState) {
                    AccountState.AUTHENTICATED ->
                        BrowserFragmentDirections.actionGlobalAccountSettingsFragment()
                    AccountState.NEEDS_REAUTHENTICATION ->
                        BrowserFragmentDirections.actionGlobalAccountProblemFragment(
                            entrypoint = MidoriFxAEntryPoint.BrowserToolbar,
                        )
                    AccountState.NO_ACCOUNT ->
                        BrowserFragmentDirections.actionGlobalTurnOnSync(entrypoint = MidoriFxAEntryPoint.BrowserToolbar)
                }
                browserAnimator.captureEngineViewAndDrawStatically {
                    navController.nav(
                        R.id.browserFragment,
                        directions
                    )
                }
            }
            is ToolbarMenu.Item.RequestDesktop -> {
                currentSession?.let {
                    sessionUseCases.requestDesktopSite.invoke(
                        item.isChecked,
                        it.id
                    )
                }
            }
            is ToolbarMenu.Item.AddToTopSites -> {
                scope.launch {
                    val context = swipeRefresh.context
                    val numPinnedSites = topSitesStorage.cachedTopSites
                        .filter { it is TopSite.Default || it is TopSite.Pinned }.size

                    if (numPinnedSites >= settings.topSitesMaxLimit) {
                        AlertDialog.Builder(swipeRefresh.context).apply {
                            setTitle(R.string.shortcut_max_limit_title)
                            setMessage(R.string.shortcut_max_limit_content)
                            setPositiveButton(R.string.top_sites_max_limit_confirmation_button) { dialog, _ ->
                                dialog.dismiss()
                            }
                            create()
                        }.show()
                    } else {
                        ioScope.launch {
                            currentSession?.let {
                                with(activity.components.useCases.topSitesUseCase) {
                                    addPinnedSites(it.content.title, it.content.url)
                                }
                            }
                        }.join()

                        MidoriSnackbar.make(
                            view = swipeRefresh,
                            duration = Snackbar.LENGTH_SHORT,
                            isDisplayedWithBrowserToolbar = true
                        )
                            .setText(
                                context.getString(R.string.snackbar_added_to_shortcuts)
                            )
                            .show()
                    }
                }
            }
            is ToolbarMenu.Item.AddToHomeScreen -> {
                settings.installPwaOpened = true
                MainScope().launch {
                    with(activity.components.useCases.webAppUseCases) {
                        if (isInstallable()) {
                            addToHomescreen()
                        } else {
                            val directions =
                                BrowserFragmentDirections.actionBrowserFragmentToCreateShortcutFragment()
                            navController.navigateSafe(R.id.browserFragment, directions)
                        }
                    }
                }
            }
            is ToolbarMenu.Item.FindInPage -> {
                findInPageLauncher()
            }
            is ToolbarMenu.Item.AddonsManager -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalAddonsManagementFragment()
                )
            }
            is ToolbarMenu.Item.SaveToCollection -> {
                currentSession?.let { currentSession ->
                    val directions =
                        BrowserFragmentDirections.actionGlobalCollectionCreationFragment(
                            tabIds = arrayOf(currentSession.id),
                            selectedTabIds = arrayOf(currentSession.id),
                            saveCollectionStep = if (tabCollectionStorage.cachedTabCollections.isEmpty()) {
                                SaveCollectionStep.NameCollection
                            } else {
                                SaveCollectionStep.SelectCollection
                            }
                        )
                    navController.nav(R.id.browserFragment, directions)
                }
            }
            is ToolbarMenu.Item.Bookmark -> {
                store.state.selectedTab?.let {
                    getProperUrl(it)?.let { url -> bookmarkTapped(url, it.content.title) }
                }
            }
            is ToolbarMenu.Item.Bookmarks -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalBookmarkFragment(BookmarkRoot.Mobile.id)
                )
            }
            is ToolbarMenu.Item.History -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalHistoryFragment()
                )
            }

            is ToolbarMenu.Item.Downloads -> browserAnimator.captureEngineViewAndDrawStatically {
                navController.nav(
                    R.id.browserFragment,
                    BrowserFragmentDirections.actionGlobalDownloadsFragment()
                )
            }
            is ToolbarMenu.Item.NewTab -> {
                navController.navigate(
                    BrowserFragmentDirections.actionGlobalHome(focusOnAddressBar = true)
                )
            }
            is ToolbarMenu.Item.SetDefaultBrowser -> {
                activity.openSetDefaultBrowserOption()
            }
            is ToolbarMenu.Item.RemoveFromTopSites -> {
                scope.launch {
                    val removedTopSite: TopSite? =
                        pinnedSiteStorage
                            .getPinnedSites()
                            .find { it.url == currentSession?.content?.url }
                    if (removedTopSite != null) {
                        ioScope.launch {
                            currentSession?.let {
                                with(activity.components.useCases.topSitesUseCase) {
                                    removeTopSites(removedTopSite)
                                }
                            }
                        }.join()
                    }

                    MidoriSnackbar.make(
                        view = swipeRefresh,
                        duration = Snackbar.LENGTH_SHORT,
                        isDisplayedWithBrowserToolbar = true
                    )
                        .setText(
                            swipeRefresh.context.getString(R.string.snackbar_top_site_removed)
                        )
                        .show()
                }
            }
        }
    }

    private fun getProperUrl(currentSession: SessionState?): String? {
        return currentSession?.id?.let {
            val currentTab = browserStore.state.findTab(it)
            if (currentTab?.readerState?.active == true) {
                currentTab.readerState.activeUrl
            } else {
                currentSession.content.url
            }
        }
    }
}
