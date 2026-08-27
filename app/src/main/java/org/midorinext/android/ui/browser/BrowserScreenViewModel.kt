package org.midorinext.android.ui.browser

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.adblock.MidoriPrivacyFeature
import org.midorinext.android.mozac.pdf.PdfSaveEvents
import org.midorinext.android.vpn.MidoriVpnFeature
import org.midorinext.android.ext.isLegacyMidoriHomeUrl
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.storage.bookmarks.BookmarksRepository
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
import mozilla.components.browser.state.action.TranslationsAction
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.pageextraction.ContentParams
import mozilla.components.concept.engine.translate.Language
import mozilla.components.concept.engine.translate.LanguageSetting
import mozilla.components.concept.engine.translate.TranslationOptions
import mozilla.components.concept.engine.translate.TranslationPageSettingOperation
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
    val pdfSaveEvents: PdfSaveEvents,
): ViewModel() {
    data class TranslationSheetState(
        val enabled: Boolean = false,
        val sourceLanguage: String? = null,
        val targetLanguage: String? = null,
        val sourceLanguages: List<Language> = emptyList(),
        val targetLanguages: List<Language> = emptyList(),
        val offerTranslation: Boolean = true,
        val alwaysTranslateSource: Boolean = false,
        val neverTranslateSource: Boolean = false,
        val neverTranslateSite: Boolean = false,
    )

    data class SelectedTabSnapshot(
        val id: String,
        val url: String,
    )

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

    val restoreComplete = store.flow()
        .map { state -> state.restoreComplete }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false,
        )

    val selectedTabSnapshot = store.flow()
        .map { state ->
            state.selectedTab?.let { tab ->
                SelectedTabSnapshot(id = tab.id, url = tab.content.url)
            }
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null,
        )

    val appPreferences = appPreferencesRepository.flow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppPreferencesSerializer.defaultValue
        )

    private var openBlankNewTab by mutableStateOf(false)
    private val tabsWithUserNavigationInFlight = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            appPreferencesRepository.flow.collect { prefs ->
                openBlankNewTab = prefs.openBlankNewTab
            }
        }
        viewModelScope.launch {
            store.flow().collect { state ->
                val replaceableTabIds = state.tabs
                    .filter { tab ->
                        tab.content.url.isLegacyMidoriHomeUrl() ||
                            MidoriUseCases.isNewTabLoadingUrl(tab.content.url)
                    }
                    .mapTo(mutableSetOf()) { tab -> tab.id }
                tabsWithUserNavigationInFlight.retainAll(replaceableTabIds)
            }
        }
    }

    val newTabState = MidoriUseCases.newTabState

    val newTabPageUrl = MidoriUseCases.newTabPageUrl

    val currentEngineSession = store.flow()
        .map { state -> state.selectedTab?.engineState?.engineSession }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val canTranslateCurrentPage = combine(store.flow(), appPreferences) { state, preferences ->
            val tab = state.selectedTab
            // Language detection can arrive after the page menu is opened. Keep the action
            // available for normal pages and let translateCurrentPage wait for Gecko's detected
            // language pair before starting the native translation.
            !preferences.translationsDisabled && tab != null && !tab.content.private
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val translationSheetState = combine(store.flow(), appPreferences) { state, preferences ->
            val tab = state.selectedTab
            val detectedLanguages = tab?.translationsState?.translationEngineState?.detectedLanguages
            val supportedLanguages = state.translationEngine.supportedLanguages
            val sourceLanguage = detectedLanguages?.documentLangTag?.takeIf { it.isNotBlank() }
            val languageSetting = sourceLanguage?.let { state.translationEngine.languageSettings?.get(it) }

            TranslationSheetState(
                enabled = !preferences.translationsDisabled && tab != null && !tab.content.private,
                sourceLanguage = sourceLanguage,
                targetLanguage = detectedLanguages?.userPreferredLangTag?.takeIf { it.isNotBlank() },
                sourceLanguages = supportedLanguages?.fromLanguages.orEmpty().distinctBy { it.code },
                targetLanguages = supportedLanguages?.toLanguages.orEmpty().distinctBy { it.code },
                offerTranslation = state.translationEngine.offerTranslation ?: true,
                alwaysTranslateSource = languageSetting == LanguageSetting.ALWAYS,
                neverTranslateSource = languageSetting == LanguageSetting.NEVER,
                neverTranslateSite = tab?.translationsState?.pageSettings?.neverTranslateSite ?: false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TranslationSheetState()
        )

    var isUrlBookmarked = urlFlow
        .filterNotNull()
        .flatMapLatest { bookmarksRepository.isUrlBookmarkedFlow(it) }
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

    val isMidoriPrivacyActionAvailable = store.flow()
        .map { state ->
            val extension = state.extensions[MidoriPrivacyFeature.EXTENSION_ID]
            extension?.enabled == true && extension.browserAction?.enabled != false
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false,
        )

    val isMidoriVpnActionAvailable = store.flow()
        .map { state ->
            val extension = state.extensions[MidoriVpnFeature.EXTENSION_ID]
            extension?.enabled == true && extension.browserAction?.enabled != false
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false,
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

    fun switchTab(direction: Int) {
        val selectedTab = store.state.selectedTab ?: return
        val tabs = store.state.tabs.filter { it.content.private == selectedTab.content.private }
        if (tabs.size < 2) return

        val currentIndex = tabs.indexOfFirst { it.id == selectedTab.id }
        if (currentIndex < 0) return
        val targetIndex = (currentIndex + direction).floorMod(tabs.size)
        tabsUseCases.selectTab(tabs[targetIndex].id)
    }

    fun summarizeCurrentPage(onResult: (String) -> Unit, onError: () -> Unit) {
        currentEngineSession.value?.getPageContent(
            options = ContentParams(removeBoilerplate = true),
            onResult = { content -> onResult(compactSummary(content)) },
            onException = { onError() }
        ) ?: onError()
    }

    /** Starts GeckoView's on-device translation after the user confirms its language pair. */
    fun translateCurrentPage(fromLanguage: String, toLanguage: String): Boolean {
        val tab = store.state.selectedTab ?: return false
        if (tab.content.private || appPreferences.value.translationsDisabled) return false
        if (fromLanguage.isBlank() || toLanguage.isBlank()) return false
        if (sameLanguage(fromLanguage, toLanguage)) return false

        store.dispatch(
            TranslationsAction.TranslateAction(
                tabId = tab.id,
                fromLanguage = fromLanguage,
                toLanguage = toLanguage,
                options = TranslationOptions(downloadModel = true)
            )
        )
        return true
    }

    fun updateTranslationOffer(enabled: Boolean) {
        store.dispatch(TranslationsAction.UpdateGlobalOfferTranslateSettingAction(enabled))
    }

    fun updateAlwaysTranslateSource(enabled: Boolean) {
        updateTranslationPageSetting(
            operation = TranslationPageSettingOperation.UPDATE_ALWAYS_TRANSLATE_LANGUAGE,
            enabled = enabled
        )
        if (enabled) {
            updateTranslationPageSetting(
                operation = TranslationPageSettingOperation.UPDATE_NEVER_TRANSLATE_LANGUAGE,
                enabled = false
            )
        }
    }

    fun updateNeverTranslateSource(enabled: Boolean) {
        updateTranslationPageSetting(
            operation = TranslationPageSettingOperation.UPDATE_NEVER_TRANSLATE_LANGUAGE,
            enabled = enabled
        )
        if (enabled) {
            updateTranslationPageSetting(
                operation = TranslationPageSettingOperation.UPDATE_ALWAYS_TRANSLATE_LANGUAGE,
                enabled = false
            )
        }
    }

    fun updateNeverTranslateSite(enabled: Boolean) {
        updateTranslationPageSetting(
            operation = TranslationPageSettingOperation.UPDATE_NEVER_TRANSLATE_SITE,
            enabled = enabled
        )
    }

    private fun updateTranslationPageSetting(
        operation: TranslationPageSettingOperation,
        enabled: Boolean,
    ) {
        val tab = store.state.selectedTab ?: return
        store.dispatch(
            TranslationsAction.UpdatePageSettingAction(
                tabId = tab.id,
                operation = operation,
                setting = enabled
            )
        )
    }

    fun commitSearch(searchText: String, category: String? = null) {
        val trimmedSearch = searchText.trim()
        if (trimmedSearch.isBlank()) {
            toolbarState.updateFocus(false)
            return
        }

        toolbarState.updateFocus(false)
        markSelectedNewTabAsUserNavigation()
        if (trimmedSearch.isUrl()) {
            sessionUseCases.loadUrl(url = trimmedSearch.toNormalizedUrl())
        } else {
            MidoriUseCases.loadSERPPage(trimmedSearch, category)
        }
    }

    fun openNewMidoriTab(private: Boolean = false, focusToolbar: Boolean = true) {
        if (private) {
            MidoriUseCases.openPrivatePage()
        } else if (!MidoriUseCases.isNewTabEnabled && openBlankNewTab) {
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
        store.state.selectedTabId?.let { tabId ->
            tabsWithUserNavigationInFlight.remove(tabId)
            MidoriUseCases.loadNewTabPage(tabId)
        }
    }

    private fun Int.floorMod(size: Int): Int = ((this % size) + size) % size

    private fun compactSummary(content: String): String {
        val normalized = content
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return ""

        val sentences = normalized
            .split(Regex("(?<=[.!?])\\s+"))
            .filter { it.length >= 35 }
            .take(3)
        return (if (sentences.isEmpty()) listOf(normalized.take(600)) else sentences)
            .joinToString(" ")
            .take(900)
    }

    fun replaceTabWithNewTab(tabId: String, expectedUrl: String) {
        if (tabId in tabsWithUserNavigationInFlight) return
        val actualUrl = store.state.tabs
            .firstOrNull { tab -> tab.id == tabId }
            ?.content
            ?.url
        if (actualUrl != expectedUrl) return
        if (!actualUrl.isLegacyMidoriHomeUrl() && !MidoriUseCases.isNewTabLoadingUrl(actualUrl)) return

        MidoriUseCases.loadNewTabPage(tabId, replaceCurrent = true)
    }

    private fun markSelectedNewTabAsUserNavigation(): String? {
        val tab = store.state.selectedTab ?: return null
        val isReplaceable = tab.content.url.isLegacyMidoriHomeUrl() ||
            MidoriUseCases.isNewTabLoadingUrl(tab.content.url)
        if (!isReplaceable) return null

        tabsWithUserNavigationInFlight += tab.id
        return tab.id
    }

    fun isNewTabUrl(url: String?): Boolean = MidoriUseCases.isNewTabUrl(url)

    fun isNewTabLoadingUrl(url: String?): Boolean = MidoriUseCases.isNewTabLoadingUrl(url)

    val isNewTabEnabled: Boolean get() = MidoriUseCases.isNewTabEnabled

    private var safetyTabOpening = false

    fun openSafetyTabIfNeeded() {
        if (!store.state.restoreComplete || store.state.tabs.isNotEmpty() || safetyTabOpening) {
            return
        }

        safetyTabOpening = true
        try {
            openNewMidoriTab(focusToolbar = false)
        } finally {
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
            val extension = store.state.extensions[extensionId] ?: return@launch
            val action = extension.browserAction
            if (extension.enabled && action?.enabled != false) {
                action?.onClick?.invoke()
            }
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

private fun sameLanguage(first: String?, second: String?): Boolean =
    first?.substringBefore('-')?.equals(second?.substringBefore('-'), ignoreCase = true) == true
