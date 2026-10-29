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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*
import mozilla.components.browser.icons.BrowserIcons

open class ToolbarState(
    val browserIcons: BrowserIcons,
    datahub: Lazy<Datahub>,
    private val suggestionProviders: Lazy<List<SuggestionProvider>>,
    coroutineScope: CoroutineScope = MainScope(),
    preferences: Flow<AppPreferences> = flowOf(AppPreferences.getDefaultInstance()),
    privateMode: Flow<Boolean> = flowOf(false),
) {
    val datahub by datahub
    var text by mutableStateOf(TextFieldValue(""))
        internal set

    var hasFocus by mutableStateOf(false)
        private set

    var isQueryPrefilled by mutableStateOf(false)
        private set

    var searchEngineOverride by mutableStateOf<String?>(null)

    val voiceSearchEnabled = preferences.map { !it.disableVoiceSearch }
        .stateIn(coroutineScope, SharingStarted.Eagerly, true)

    private val emptySuggestions = emptyMap<SuggestionProvider, List<Suggestion>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions: StateFlow<Map<SuggestionProvider, List<Suggestion>>> = combine(snapshotFlow {
        SuggestionQuery(
            text = text.getTextBeforeSelection(text.text.length).text,
            hasFocus = hasFocus,
            isPrefilled = isQueryPrefilled,
            engineOverride = searchEngineOverride,
        )
    }.distinctUntilChanged(), preferences, privateMode) { query, prefs, isPrivate ->
        Triple(query, prefs, isPrivate)
    }.flatMapLatest { (query, prefs, isPrivate) ->
        flow<Map<SuggestionProvider, List<Suggestion>>> {
            emit(emptySuggestions)
            if (!query.hasFocus || (query.isPrefilled && query.text.isNotBlank())) return@flow
            delay(100)
            val providers = suggestionProviders.value.filter { provider ->
                (query.text.isNotBlank() || provider.supportsEmptyQuery) && provider.isEnabled(prefs, isPrivate)
            }
            emitAll(channelFlow<Pair<SuggestionProvider, List<Suggestion>>> {
                providers.forEach { provider ->
                    launch {
                        val results = try {
                            if (provider is MidoriSuggestProvider) {
                                provider.getSuggestions(query.text, query.engineOverride, isPrivate)
                            } else {
                                provider.getSuggestions(query.text)
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            emptyList()
                        }
                        send(provider to results)
                    }
                }
            }.runningFold(providers.associateWith { emptyList<Suggestion>() }) { results, (provider, items) ->
                results + (provider to items)
            })
        }
    }
        .buffer(0)
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
