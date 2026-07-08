package org.midorinext.android.ui.browser.suggest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.SessionTabsProvider
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.concept.storage.HistoryStorage


@Composable
fun Suggest(
    suggestions: Map<SuggestionProvider, List<Suggestion>>,
    onSuggestionClicked: (suggestion: Suggestion) -> Unit,
    onSetTextClicked: (text: String) -> Unit,
    toolbarPosition: ToolbarPosition,
    browserIcons: BrowserIcons,
    modifier: Modifier = Modifier
) {
    // TODO provide this from somewhere else ! (SuggestionState ?)
    val providersOrdered = remember(suggestions.keys) {
        listOf(
            suggestions.keys.find { it is ClipboardProvider },
            suggestions.keys.find { it is MidoriSuggestProvider },
            suggestions.keys.find { it is DomainProvider },
            suggestions.keys.find { it is BookmarksStorage },
            suggestions.keys.find { it is HistoryStorage },
            suggestions.keys.find { it is SessionTabsProvider }
        )
    }

    LazyColumn(modifier = modifier
        .background(MaterialTheme.colorScheme.background)
    ) {
        providersOrdered.forEach { provider ->
            suggestions[provider]?.let { suggestions ->
                items(items = suggestions) { suggestion ->
                    SuggestItem( // TODO add key and animateItemPlacement to suggest item
                        suggestion = suggestion,
                        toolbarPosition = toolbarPosition,
                        browserIcons = browserIcons,
                        onSetTextClicked = onSetTextClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { onSuggestionClicked(suggestion) }
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}