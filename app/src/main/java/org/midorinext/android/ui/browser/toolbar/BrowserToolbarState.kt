package org.midorinext.android.ui.browser.toolbar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.midorinext.android.ext.getMidoriSERPSearch
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ext.urlDecode
import org.midorinext.android.newtab.MidoriNewTabFeature
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.stats.Datahub
import org.midorinext.android.suggest.SuggestionProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow

@AssistedFactory
interface BrowserToolbarStateFactory {
    fun create(coroutineScope: CoroutineScope = MainScope()) : BrowserToolbarState
}

class BrowserToolbarState @AssistedInject constructor(
    private val store: BrowserStore,
    appPreferencesRepository: AppPreferencesRepository,
    suggestionProviders: dagger.Lazy<@JvmSuppressWildcards List<SuggestionProvider>>,
    browserIcons: BrowserIcons,
    datahub: dagger.Lazy<Datahub>,
    private val newTabFeature: MidoriNewTabFeature,
    @Assisted private val coroutineScope: CoroutineScope = MainScope()
): ToolbarState(
    browserIcons, lazy { datahub.get() }, lazy { suggestionProviders.get() }, coroutineScope,
    appPreferencesRepository.flow,
    store.flow().map { it.selectedTab?.content?.private == true },
) {
    var visible by mutableStateOf(true)
        private set

    var onMidori by mutableStateOf(
        store.state.selectedTab?.content?.url?.let { it.isMidoriUrl() || newTabFeature.isNewTabUrl(it) } ?: true
    )
        private set

    var showSiteSecurity by mutableStateOf(false)
        private set

    var trueHeightPx by mutableIntStateOf(0)
        private set

    val searchPreferences = appPreferencesRepository.flow.stateIn(
        scope = coroutineScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppPreferences.getDefaultInstance(),
    )

    val isPrivateMode = store.flow()
        .map { it.selectedTab?.content?.private == true }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000L), false)

    val toolbarPosition = appPreferencesRepository.flow
        .map { preferences ->
            if (preferences.toolbarPosition == ToolbarPosition.BOTTOM) ToolbarPosition.BOTTOM
            else ToolbarPosition.TOP
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ToolbarPosition.TOP
        )

    val recentSearches = combine(
        appPreferencesRepository.flow,
        store.flow().map { it.selectedTab?.content?.private == true },
    ) { prefs, privateMode ->
        if (privateMode || prefs.disableRecentSearches) emptyList() else prefs.recentSearchesList
    }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val shouldHideOnScroll = appPreferencesRepository.flow
        .map { prefs -> prefs.hideToolbarOnScroll }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val loadingProgress = store.flow()
        .map { state -> state.selectedTab?.content?.progress?.toFloat()?.div(100) }
        .filterNotNull()
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0f
        )

    val siteSecurity = store.flow()
        .map { state -> state.selectedTab?.content?.securityInfo }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private var selectedTabId = store.state.selectedTabId

    private val selectedPage = store.flow()
        .map { state -> state.selectedTab?.let { SelectedPage(it.id, it.content.url) } }
        .distinctUntilChanged()

    val currentUrl = selectedPage
        .combine(newTabFeature.legacyOrigins) { page, _ -> page }
        .onEach { page ->
            if (page?.tabId != selectedTabId) {
                selectedTabId = page?.tabId
                updateFocus(false)
            }
            if (!hasFocus) {
                updateTextWithUrl(page?.url.orEmpty())
                updateVisibility(true)
            }
            onMidori = page?.url?.let { it.isMidoriUrl() || newTabFeature.isNewTabUrl(it) } ?: true
        }
        .map { it?.url }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = store.state.selectedTab?.content?.url
        )

    init {
        updateTextWithUrl(store.state.selectedTab?.content?.url.orEmpty())
    }

    override fun updateFocus(hasFocus: Boolean) {
        if (hasFocus == this.hasFocus) return
        super.updateFocus(hasFocus)
        if (!hasFocus) searchEngineOverride = null
        if (hasFocus) {
            updateVisibility(true)
        }
        updateTextWithUrl(store.state.selectedTab?.content?.url.orEmpty())
    }

    private fun updateTextWithUrl(url: String) {
        text = if (newTabFeature.isNewTabUrl(url)) {
            TextFieldValue("")
        } else if (url.isMidoriUrl()) {
            url.getMidoriSERPSearch()?.urlDecode()?.let { search ->
                if (hasFocus) TextFieldValue(search, selection = TextRange(0, search.length))
                else TextFieldValue(search)
            } ?: TextFieldValue("")
        } else if (!hasFocus) {
            TextFieldValue(url.toCleanHost())
        } else {
            // TODO Constraint url to a maximum size
            TextFieldValue(url, selection = TextRange(0, url.length))
        }
    }

    fun updateVisibility(visible: Boolean) {
        this.visible = visible
    }

    fun updateShowSiteSecurity(visible: Boolean) {
        this.showSiteSecurity = visible
    }

    internal fun updateTrueHeightPx(height: Int) {
        trueHeightPx = height
    }

    private data class SelectedPage(val tabId: String, val url: String)
}
