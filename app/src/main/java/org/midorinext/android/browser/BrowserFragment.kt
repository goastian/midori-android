/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.browser.toolbar.BrowserToolbar
import mozilla.components.concept.awesomebar.AwesomeBar.Suggestion
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.awesomebar.AwesomeBarFeature
import mozilla.components.feature.awesomebar.provider.SearchSuggestionProvider
import mozilla.components.feature.readerview.view.ReaderViewControlsBar
import mozilla.components.feature.syncedtabs.SyncedTabsStorageSuggestionProvider
import mozilla.components.feature.tabs.WindowFeature
import mozilla.components.feature.tabs.toolbar.TabsToolbarFeature
import mozilla.components.feature.toolbar.WebExtensionToolbarFeature
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.feature.ViewBoundFeatureWrapper
import org.midorinext.android.R
import org.midorinext.android.ext.components
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.bookmarks.BookmarksFragment
import org.midorinext.android.home.HomeCustomization
import org.midorinext.android.home.HomeCustomizationSheet
import org.midorinext.android.home.HomeCustomizationStorage
import org.midorinext.android.home.HomeScreen
import org.midorinext.android.home.ShortcutAction
import org.midorinext.android.home.SuggestedSite
import org.midorinext.android.home.TopSite
import org.midorinext.android.theme.MidoriTheme
import org.midorinext.android.search.AwesomeBarWrapper
import org.midorinext.android.tabs.TabsTrayFragment

/**
 * Fragment used for browsing the web within the main app.
 */
class BrowserFragment :
    BaseBrowserFragment(),
    UserInteractionHandler {
    private val thumbnailsFeature = ViewBoundFeatureWrapper<BrowserThumbnails>()
    private val readerViewFeature = ViewBoundFeatureWrapper<ReaderViewIntegration>()
    private val webExtToolbarFeature = ViewBoundFeatureWrapper<WebExtensionToolbarFeature>()
    private val windowFeature = ViewBoundFeatureWrapper<WindowFeature>()

    private val awesomeBar: AwesomeBarWrapper
        get() = requireView().findViewById(R.id.awesomeBar)
    private val toolbar: BrowserToolbar
        get() = requireView().findViewById(R.id.toolbar)
    private val engineView: EngineView
        get() = requireView().findViewById<View>(R.id.engineView) as EngineView
    private val homeView: ComposeView
        get() = requireView().findViewById(R.id.homeView)
    private val readerViewBar: ReaderViewControlsBar
        get() = requireView().findViewById(R.id.readerViewBar)
    private val readerViewAppearanceButton: FloatingActionButton
        get() = requireView().findViewById(R.id.readerViewAppearanceButton)

    private val topSites = mutableStateListOf<TopSite>()
    private val suggestedSites = mutableStateListOf<SuggestedSite>()
    private val trackersBlocked = mutableIntStateOf(0)
    private var customization by mutableStateOf(
        HomeCustomization(
            wallpaperName = HomeCustomizationStorage.WALLPAPER_NONE,
            customColor = null,
            showSpeedDials = true,
            showSuggestedSites = false,
            speedDialSize = org.midorinext.android.home.SpeedDialSize.MEDIUM,
            showCustomizeButton = true,
        ),
    )
    private var showCustomizationSheet by mutableStateOf(false)
    private lateinit var customizationStorage: HomeCustomizationStorage

    override val shouldUseComposeUI: Boolean
        get() = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(
            getString(R.string.pref_key_compose_ui),
            false,
        )

    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        customizationStorage = HomeCustomizationStorage(requireContext())
        customization = customizationStorage.load()

        AwesomeBarFeature(awesomeBar, toolbar, engineView)
            .addSearchProvider(
                requireComponents.core.store,
                requireComponents.useCases.searchUseCases.defaultSearch,
                fetchClient = requireComponents.core.client,
                mode = SearchSuggestionProvider.Mode.MULTIPLE_SUGGESTIONS,
                engine = requireComponents.core.engine,
                limit = 5,
                filterExactMatch = true,
            ).addSessionProvider(
                resources,
                requireComponents.core.store,
                requireComponents.useCases.tabsUseCases.selectTab,
            ).addHistoryProvider(
                requireComponents.core.historyStorage,
                requireComponents.useCases.sessionUseCases.loadUrl,
            ).addClipboardProvider(requireContext(), requireComponents.useCases.sessionUseCases.loadUrl)

        // We cannot really add a `addSyncedTabsProvider` to `AwesomeBarFeature` coz that would create
        // a dependency on feature-syncedtabs (which depends on Sync).
        awesomeBar.addProviders(
            SyncedTabsStorageSuggestionProvider(
                requireComponents.backgroundServices.syncedTabsStorage,
                requireComponents.useCases.tabsUseCases.addTab,
                requireComponents.core.icons,
            ),
        )
        awesomeBar.setOnRemoveSuggestionButtonClicked {
            awesomeBar.addHiddenSuggestion(it)
            (it.suggestion as? Suggestion)?.let { s -> deleteHistorySuggestion(s) }
        }

        // Only add tab counter to toolbar when toolbar is at bottom (no nav bar)
        val isToolbarBottom = PreferenceManager.getDefaultSharedPreferences(requireContext())
            .getString(requireContext().getPreferenceKey(R.string.pref_key_toolbar_position), "bottom") != "top"
        if (isToolbarBottom) {
            TabsToolbarFeature(
                toolbar = toolbar,
                sessionId = sessionId,
                store = requireComponents.core.store,
                showTabs = ::showTabs,
                lifecycleOwner = this,
            )
        }

        thumbnailsFeature.set(
            feature = BrowserThumbnails(
                requireContext(),
                engineView,
                requireComponents.core.store,
            ),
            owner = this,
            view = view,
        )

        readerViewFeature.set(
            feature = ReaderViewIntegration(
                requireContext(),
                requireComponents.core.engine,
                requireComponents.core.store,
                toolbar,
                readerViewBar,
                readerViewAppearanceButton,
            ),
            owner = this,
            view = view,
        )

        webExtToolbarFeature.set(
            feature = WebExtensionToolbarFeature(
                toolbar,
                requireContext().components.core.store,
            ),
            owner = this,
            view = view,
        )

        windowFeature.set(
            feature = WindowFeature(
                store = requireComponents.core.store,
                tabsUseCases = requireComponents.useCases.tabsUseCases,
            ),
            owner = this,
            view = view,
        )

        engineView.setDynamicToolbarMaxHeight(resources.getDimensionPixelSize(R.dimen.browser_toolbar_height))

        // Connect navigation bar tabs button
        view.findViewById<View>(R.id.navButtonTabs)?.setOnClickListener {
            showTabs()
        }
        view.findViewById<View>(R.id.navButtonTabsContainer)?.setOnClickListener {
            showTabs()
        }

        setupHomeScreen()
        observeUrlForHome()
        loadSuggestedSites()
    }

    private fun setupHomeScreen() {
        topSites.addAll(getDefaultTopSites())

        homeView.setContent {
            MidoriTheme {
                HomeScreen(
                    topSites = topSites,
                    suggestedSites = suggestedSites,
                    trackersBlocked = trackersBlocked.intValue,
                    customization = customization,
                    onSearchBarClick = {
                        hideHome()
                        toolbar.editMode()
                    },
                    onTopSiteClick = { site ->
                        hideHome()
                        requireComponents.useCases.sessionUseCases.loadUrl(site.url)
                    },
                    onSuggestedSiteClick = { site ->
                        hideHome()
                        requireComponents.useCases.sessionUseCases.loadUrl(site.url)
                    },
                    onShortcutClick = { action -> handleShortcut(action) },
                    onCustomizeClick = { showCustomizationSheet = true },
                )

                if (showCustomizationSheet) {
                    HomeCustomizationSheet(
                        currentCustomization = customization,
                        onDismiss = { showCustomizationSheet = false },
                        onWallpaperSelected = { wallpaper ->
                            customizationStorage.saveWallpaper(wallpaper)
                            customization = customizationStorage.load()
                        },
                        onCustomColorSelected = { color ->
                            customizationStorage.saveCustomColor(color)
                            customization = customizationStorage.load()
                        },
                        onShowSpeedDialsChanged = { show ->
                            customizationStorage.saveShowSpeedDials(show)
                            customization = customizationStorage.load()
                        },
                        onShowSuggestedSitesChanged = { show ->
                            customizationStorage.saveShowSuggestedSites(show)
                            customization = customizationStorage.load()
                            if (show) loadSuggestedSites()
                        },
                        onSpeedDialSizeChanged = { size ->
                            customizationStorage.saveSpeedDialSize(size)
                            customization = customizationStorage.load()
                        },
                        onShowCustomizeButtonChanged = { show ->
                            customizationStorage.saveShowCustomizeButton(show)
                            customization = customizationStorage.load()
                        },
                    )
                }
            }
        }
    }

    private fun loadSuggestedSites() {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                try {
                    requireComponents.core.historyStorage.getDetailedVisits(
                        start = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
                        end = System.currentTimeMillis(),
                        excludeTypes = listOf(
                            mozilla.components.concept.storage.VisitType.DOWNLOAD,
                            mozilla.components.concept.storage.VisitType.REDIRECT_PERMANENT,
                            mozilla.components.concept.storage.VisitType.REDIRECT_TEMPORARY,
                        ),
                    )
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val topDomains = mutableMapOf<String, Pair<String, Int>>()
            history.forEach { visit ->
                val url = visit.url
                val domain = url.removePrefix("https://").removePrefix("http://")
                    .split("/").firstOrNull() ?: return@forEach
                val title = visit.title ?: ""
                val existing = topDomains[domain]
                if (existing == null) {
                    topDomains[domain] = Pair(if (title.isNotEmpty()) title else url, 1)
                } else {
                    topDomains[domain] = Pair(
                        if (existing.first.length > title.length && title.isNotEmpty()) title else existing.first,
                        existing.second + 1,
                    )
                }
            }

            // Filter out default top sites domains
            val defaultDomains = getDefaultTopSites().map { site ->
                site.url.removePrefix("https://").removePrefix("http://")
                    .split("/").firstOrNull()?.lowercase() ?: ""
            }.toSet()

            val suggested = topDomains
                .filter { (domain, _) -> domain.lowercase() !in defaultDomains }
                .entries
                .sortedByDescending { it.value.second }
                .take(6)
                .map { (domain, pair) ->
                    SuggestedSite(
                        title = pair.first,
                        url = "https://$domain",
                    )
                }

            suggestedSites.clear()
            suggestedSites.addAll(suggested)
        }
    }

    private fun observeUrlForHome() {
        lifecycleScope.launch {
            requireComponents.core.store.flow()
                .map { state -> state.selectedTab?.content?.url.orEmpty() }
                .distinctUntilChanged()
                .collect { url ->
                    if (isHomeUrl(url)) {
                        showHome()
                    } else {
                        hideHome()
                    }
                }
        }
    }

    private fun isHomeUrl(url: String): Boolean =
        url.isEmpty() || url == "about:blank" || url == "about:home" || url == "about:privatebrowsing"

    private fun showHome() {
        view?.findViewById<ComposeView>(R.id.homeView)?.visibility = View.VISIBLE
    }

    private fun hideHome() {
        view?.findViewById<ComposeView>(R.id.homeView)?.visibility = View.GONE
    }

    private fun handleShortcut(action: ShortcutAction) {
        when (action) {
            ShortcutAction.NEW_PRIVATE_TAB -> {
                requireComponents.useCases.tabsUseCases.addTab(
                    url = "about:privatebrowsing",
                    selectTab = true,
                    private = true,
                )
            }
            ShortcutAction.HISTORY -> {
                showHistory()
            }
            ShortcutAction.BOOKMARKS -> {
                showBookmarks()
            }
            ShortcutAction.DOWNLOADS -> {
                showDownloads()
            }
        }
    }

    private fun getDefaultTopSites(): List<TopSite> = listOf(
        TopSite(title = "MiWallet", url = "https://wallet.astian.org"),
        TopSite(title = "Ebay", url = "https://ebay.us/gW9r3z"),
        TopSite(title = "YouTube", url = "https://youtube.com"),
        TopSite(title = "Reddit", url = "https://reddit.com"),
        TopSite(title = "GitHub", url = "https://github.com"),
        TopSite(title = "X", url = "https://x.com"),
        TopSite(title = "Amazon", url = "https://www.amazon.com/?&_encoding=UTF8&tag=astian-20&linkCode=ur2&linkId=92724227da90468d86b519b08012ac10&camp=1789&creative=9325"),
        TopSite(title = "DuckDuckGo", url = "https://duckduckgo.com"),
    )

    override fun onTabsButtonClicked() {
        showTabs()
    }

    private fun showTabs() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right,
            )
            replace(R.id.container, TabsTrayFragment())
            commit()
        }
    }

    private fun showBookmarks() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right,
            )
            replace(R.id.container, BookmarksFragment())
            commit()
        }
    }

    private fun showHistory() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right,
            )
            replace(R.id.container, org.midorinext.android.history.HistoryFragment())
            commit()
        }
    }

    private fun showDownloads() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.slide_in_left, R.anim.slide_out_right,
            )
            replace(R.id.container, org.midorinext.android.downloads.DownloadsFragment())
            commit()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun deleteHistorySuggestion(suggestion: Suggestion) {
        lifecycleScope.launch(Dispatchers.IO) {
            suggestion.description?.let {
                requireComponents.core.historyStorage.deleteHistoryMetadataForUrl(it)
            }
        }
    }

    override fun onBackPressed(): Boolean = readerViewFeature.onBackPressed() || super.onBackPressed()

    companion object {
        fun create(sessionId: String? = null) =
            BrowserFragment().apply {
            arguments = Bundle().apply {
                putSessionId(sessionId)
            }
        }
    }
}
