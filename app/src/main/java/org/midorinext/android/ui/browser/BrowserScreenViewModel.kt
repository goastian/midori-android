package org.midorinext.android.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.adblock.AdBlockerState
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import org.midorinext.android.storage.readinglist.ReadingListRepository
import org.midorinext.android.ui.browser.toolbar.BrowserToolbarState
import org.midorinext.android.ui.browser.toolbar.BrowserToolbarStateFactory
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.downloads.manager.DownloadManager
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.ktx.kotlin.isUrl
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import mozilla.components.support.ktx.kotlinx.coroutines.flow.ifAnyChanged
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BrowserScreenViewModel @Inject constructor(
    private val bookmarksRepository: BookmarksRepository,
    private val readingListRepository: ReadingListRepository,
    private val webAppUseCases: WebAppUseCases,
    val sessionUseCases: SessionUseCases,
    val tabsUseCases: TabsUseCases,
    val contextMenuUseCases: ContextMenuUseCases,
    val downloadUseCases: DownloadsUseCases,
    val fileSizeFormatter: FileSizeFormatter,
    val permissionStorage: GeckoSitePermissionsStorage,
    val browserIcons: BrowserIcons,
    val downloadManager: DownloadManager,
    val store: BrowserStore,
    val engine: Engine,
    val client: Client,
    val MidoriUseCases: MidoriUseCases,
    private val appPreferencesRepository: AppPreferencesRepository,
    val contentBlockerState: ContentBlockerState,
    val adBlockerState: AdBlockerState
): ViewModel() {
    data class InstalledMenuExtension(
        val id: String,
        val name: String,
        val badgeText: String?,
        val hasBrowserAction: Boolean,
    )

    @Inject lateinit var toolbarStateFactory: BrowserToolbarStateFactory
    val toolbarState: BrowserToolbarState by lazy {
        toolbarStateFactory.create(viewModelScope)
    }

    val tabCount = store.flow()
        .ifAnyChanged { s -> arrayOf(s.tabs.size, s.selectedTab?.content?.private) }
        .map { state -> state.tabs.count { it.content.private == state.selectedTab?.content?.private } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    private val urlFlow = store.flow()
        .map { state -> state.selectedTab?.content?.url }
        .distinctUntilChanged()

    val currentUrl = urlFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val appPreferences = appPreferencesRepository.flow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferencesSerializer.defaultValue
        )

    var openBlankNewTab by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            appPreferencesRepository.flow.collect { prefs ->
                openBlankNewTab = prefs.openBlankNewTab
            }
        }
    }

    val currentEngineSession = store.flow()
        .map { state -> state.selectedTab?.engineState?.engineSession }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    var isUrlBookmarked = urlFlow
        .filterNotNull()
        .flatMapLatest { bookmarksRepository.isUrlBookmarkedFlow(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val isUrlInReadingList = urlFlow
        .filterNotNull()
        .flatMapLatest { readingListRepository.isUrlSavedFlow(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val hasReadingModeItems = readingListRepository.hasItemsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    fun addBookmark() {
        store.state.selectedTab?.let { tab ->
            viewModelScope.launch(Dispatchers.IO) {
                bookmarksRepository.addItem(
                    parentGuid = bookmarksRepository.root.guid,
                    url = tab.content.url,
                    title = tab.content.title,
                    position = null
                )
            }
        }
    }

    fun removeBookmark() {
        store.state.selectedTab?.content?.url?.let { url ->
            viewModelScope.launch(Dispatchers.IO) {
                bookmarksRepository.deleteBookmarksByUrl(url)
            }
        }
    }

    fun addCurrentPageToReadingList() {
        val tab = store.state.selectedTab ?: return
        val url = tab.content.url
        if (url.isBlank() || url == "about:blank" || !url.isUrl()) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            readingListRepository.addOrUpdate(url, tab.content.title)
        }
    }

    fun removeCurrentPageFromReadingList() {
        store.state.selectedTab?.content?.url?.let { url ->
            viewModelScope.launch(Dispatchers.IO) {
                readingListRepository.deleteByUrl(url)
            }
        }
    }

    val canGoBack = store.flow()
        .map { state -> state.selectedTab?.content?.canGoBack ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val canGoForward = store.flow()
        .map { state -> state.selectedTab?.content?.canGoForward ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val desktopMode = store.flow()
        .map { state -> state.selectedTab?.content?.desktopMode ?: false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val installedMenuExtensions = store.flow()
        .map { state ->
            state.extensions.values
                .filter { !it.isBuiltIn }
                .map { extension -> extension.toInstalledMenuExtension() }
                .sortedBy { it.name.lowercase() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    var showFindInPage by mutableStateOf(false)
        private set

    val isShortcutSupported = webAppUseCases.isPinningSupported()

    fun addShortcutToHomeScreen() {
        viewModelScope.launch {
            webAppUseCases.addToHomescreen()
        }
    }

    val reloadUrl = sessionUseCases.reload
    val stopLoading = sessionUseCases.stopLoading
    val goBack = sessionUseCases.goBack
    val goForward = sessionUseCases.goForward
    val requestDesktopSite = sessionUseCases.requestDesktopSite

    fun commitSearch(searchText: String, category: String? = null) {
        val trimmedSearch = searchText.trim()
        if (trimmedSearch.isBlank()) {
            toolbarState.updateFocus(false)
            return
        }

        toolbarState.updateFocus(false)
        if (trimmedSearch.isUrl()) {
            sessionUseCases.loadUrl(url = trimmedSearch.toNormalizedUrl())
        } else {
            MidoriUseCases.loadSERPPage(trimmedSearch, category)
        }
    }

    fun openNewMidoriTab(private: Boolean = false, focusToolbar: Boolean = true) {
        if (private) {
            MidoriUseCases.openPrivatePage()
        } else if (openBlankNewTab) {
            tabsUseCases.addTab("", selectTab = true, private = false)
        } else {
            MidoriUseCases.openMidoriPage(private = false)
        }
        // TODO use invokeOnCompletion from store.dispatch instead of delay,
        //  but this needs MidoriUseCases to be recoded using dispatch directly
        viewModelScope.launch {
            delay(100)
            toolbarState.updateFocus(focusToolbar)
        }
    }

    fun goToHomepage() {
        toolbarState.updateFocus(false)
        sessionUseCases.loadUrl(url = MidoriUseCases.getMidoriUrl())
    }

    fun updateShowNewTabHome(show: Boolean) {
        openBlankNewTab = !show
        viewModelScope.launch { appPreferencesRepository.updateShowNewTabHome(show) }
    }

    private var safetyTabOpening = false

    fun openSafetyTabIfNeeded() {
        if (store.state.tabs.isNotEmpty() || safetyTabOpening) {
            return
        }

        viewModelScope.launch {
            safetyTabOpening = true
            delay(500)
            if (store.state.tabs.isEmpty()) {
                openNewMidoriTab(focusToolbar = false)
            }
            safetyTabOpening = false
        }
    }

    fun closeCurrentTab() {
        val state = store.state
        val selectedTab = state.selectedTab ?: return
        val closingLastTab = state.tabs.size == 1
        val closingLastNormalTab =
            !selectedTab.content.private && state.tabs.count { !it.content.private } == 1

        toolbarState.updateFocus(false)
        tabsUseCases.removeTab(selectedTab.id, selectParentIfExists = true)
        if (closingLastTab || closingLastNormalTab) {
            openNewMidoriTab(private = false, focusToolbar = false)
        }
    }

    fun updateShowFindInPage(show: Boolean) {
        toolbarState.updateVisibility(!show)
        showFindInPage = show
    }

    fun triggerInstalledExtensionAction(extensionId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            store.state.extensions[extensionId]?.browserAction?.onClick?.invoke()
        }
    }

    private fun WebExtensionState.toInstalledMenuExtension(): InstalledMenuExtension {
        val displayName = name?.takeIf { it.isNotBlank() } ?: id
        return InstalledMenuExtension(
            id = id,
            name = displayName,
            badgeText = browserAction?.badgeText,
            hasBrowserAction = browserAction != null,
        )
    }
}
