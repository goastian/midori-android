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
import kotlinx.coroutines.delay
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

    private val emptySuggestions = suggestionProviders.associateWith { emptyList<Suggestion>() }

    @OptIn(ExperimentalCoroutinesApi::class)
    val suggestions = snapshotFlow { text.getTextBeforeSelection(text.text.length).text }
        .distinctUntilChanged()
        .mapLatest { search ->
            delay(100)
            if (hasFocus && search.isNotBlank()) {
                suggestionProviders.associateWith { provider -> provider.getSuggestions(search) }
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

    internal open fun updateFocus(hasFocus: Boolean) {
        this.hasFocus = hasFocus
    }
}
