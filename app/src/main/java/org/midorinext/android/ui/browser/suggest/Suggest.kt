package org.midorinext.android.ui.browser.suggest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
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
    recentSearches: List<String> = emptyList(),
    showRecentSearches: Boolean = false,
    onRecentSearchClicked: (String) -> Unit = {},
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
        .background(MaterialTheme.colorScheme.surface)
    ) {
        if (showRecentSearches && recentSearches.isNotEmpty()) {
            item(key = "recent-searches-heading") {
                Text(
                    text = stringResource(R.string.browser_recent_searches),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
            items(
                items = recentSearches.take(4),
                key = { "recent-search-$it" }
            ) { search ->
                RecentSearchSuggestionItem(
                    search = search,
                    toolbarPosition = toolbarPosition,
                    onSetTextClicked = onSetTextClicked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clickable { onRecentSearchClicked(search) }
                        .padding(start = 24.dp, end = 4.dp)
                )
            }
        } else {
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
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable { onSuggestionClicked(suggestion) }
                                .padding(start = 24.dp, end = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
