package org.midorinext.android.ui.browser.toolbar

import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getTextBeforeSelection
import org.midorinext.android.stats.Datahub
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
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
    coroutineScope: CoroutineScope = MainScope()
) {
    var text by mutableStateOf(TextFieldValue(""))
        internal set

    var hasFocus by mutableStateOf(false)
        private set

    var isQueryPrefilled by mutableStateOf(false)
        private set

    private val emptySuggestions = suggestionProviders.associateWith { emptyList<Suggestion>() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions = snapshotFlow {
        SuggestionQuery(
            text = text.getTextBeforeSelection(text.text.length).text,
            hasFocus = hasFocus,
            isPrefilled = isQueryPrefilled,
        )
    }
        .distinctUntilChanged()
        .mapLatest { query ->
            delay(100)
            if (query.hasFocus && !query.isPrefilled && query.text.isNotBlank()) {
                runProvidersInParallel {
                    suggestionProviders
                        .map { provider ->
                            async { provider to provider.getSuggestions(query.text) }
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
    )
}
