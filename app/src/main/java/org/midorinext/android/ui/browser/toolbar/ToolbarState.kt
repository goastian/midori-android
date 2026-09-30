package org.midorinext.android.ui.browser.toolbar

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getTextBeforeSelection
import org.midorinext.android.stats.Datahub
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
import org.midorinext.android.suggest.providers.SessionTabsProvider
import org.midorinext.android.suggest.providers.TrendingSuggestProvider
import org.midorinext.android.preferences.app.AppPreferences
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.concept.storage.HistoryStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope as runProvidersInParallel
import kotlinx.coroutines.flow.*
import mozilla.components.browser.icons.BrowserIcons

open class ToolbarState(
    val browserIcons: BrowserIcons,
    val datahub: Datahub,
    suggestionProviders: List<SuggestionProvider>,
    coroutineScope: CoroutineScope = MainScope(),
    preferences: Flow<AppPreferences> = flowOf(AppPreferences.getDefaultInstance()),
    privateMode: Flow<Boolean> = flowOf(false),
) {
    var text by mutableStateOf(TextFieldValue(""))
        internal set

    var hasFocus by mutableStateOf(false)
        private set

    var isQueryPrefilled by mutableStateOf(false)
        private set

    var searchEngineOverride by mutableStateOf<String?>(null)

    val voiceSearchEnabled = preferences.map { !it.disableVoiceSearch }
        .stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000L), true)

    private val emptySuggestions = suggestionProviders.associateWith { emptyList<Suggestion>() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions = combine(snapshotFlow {
        SuggestionQuery(
            text = text.getTextBeforeSelection(text.text.length).text,
            hasFocus = hasFocus,
            isPrefilled = isQueryPrefilled,
            engineOverride = searchEngineOverride,
        )
    }.distinctUntilChanged(), preferences, privateMode) { query, prefs, isPrivate ->
        Triple(query, prefs, isPrivate)
    }.mapLatest { (query, prefs, isPrivate) ->
            delay(100)
            if (query.hasFocus && (!query.isPrefilled || query.text.isBlank())) {
                runProvidersInParallel {
                    suggestionProviders
                        .filter { provider ->
                            (query.text.isNotBlank() || provider.supportsEmptyQuery) &&
                                provider.isEnabled(prefs, isPrivate)
                        }
                        .map { provider ->
                            async {
                                provider to if (provider is MidoriSuggestProvider) {
                                    provider.getSuggestions(query.text, query.engineOverride, isPrivate)
                                } else {
                                    provider.getSuggestions(query.text)
                                }
                            }
                        }
                        .awaitAll()
                        .toMap()
                }
            } else emptySuggestions
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptySuggestions
        )

    internal fun updateText(text: TextFieldValue) {
        this.text = text
    }

    internal fun updateText(text: String) {
        updateText(TextFieldValue(text, selection = TextRange(text.length)))
    }

    internal fun updateTextFromUser(text: TextFieldValue) {
        isQueryPrefilled = false
        this.text = text
    }

    internal fun updateTextFromUser(text: String) {
        updateTextFromUser(TextFieldValue(text, selection = TextRange(text.length)))
    }

    internal open fun updateFocus(hasFocus: Boolean) {
        if (hasFocus && !this.hasFocus) {
            isQueryPrefilled = true
        } else if (!hasFocus) {
            isQueryPrefilled = false
        }
        this.hasFocus = hasFocus
    }

    private data class SuggestionQuery(
        val text: String,
        val hasFocus: Boolean,
        val isPrefilled: Boolean,
        val engineOverride: String?,
    )
}

private fun SuggestionProvider.isEnabled(prefs: AppPreferences, privateMode: Boolean): Boolean = when (this) {
    is MidoriSuggestProvider -> !prefs.disableSearchSuggestions &&
        (!privateMode || prefs.searchSuggestionsInPrivate)
    is TrendingSuggestProvider -> !prefs.disableSearchSuggestions && !prefs.disableTrendingSuggestions &&
        (!privateMode || prefs.searchSuggestionsInPrivate)
    is ClipboardProvider -> !prefs.disableClipboardSuggestions
    is DomainProvider -> !prefs.disableUrlAutocomplete
    is HistoryStorage -> !prefs.disableHistorySuggestions
    is BookmarksStorage -> !prefs.disableBookmarkSuggestions
    is SessionTabsProvider -> !prefs.disableTabSuggestions
    else -> true
}
