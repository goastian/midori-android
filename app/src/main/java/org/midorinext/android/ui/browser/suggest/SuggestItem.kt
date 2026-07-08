package org.midorinext.android.ui.browser.suggest

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
import org.midorinext.android.ext.toCleanUrl
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.SessionTabsProvider
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.UrlIcon
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.concept.storage.HistoryStorage


fun <T> IntRange.toAnnotatedStringRange(item: T) : AnnotatedString.Range<T> =
    AnnotatedString.Range(item, this.first, this.last + 1)

@Composable internal fun String.toSuggestAnnotatedString(
    search: String
): AnnotatedString {
    val color = LocalContentColor.current
    return AnnotatedString(
        text = this,
        spanStyles = Regex(search, setOf(RegexOption.IGNORE_CASE, RegexOption.LITERAL))
            .find(input = this)?.let { listOf(
                it.range.toAnnotatedStringRange(SpanStyle(
                    color = color.copy(0.8f),
                    fontWeight = FontWeight.Normal
                ))
            )} ?: listOf()
    )
}

// TODO decompose suggest items into multiple composable. Maybe put composable into providers ?
@Composable
fun SuggestItem(
    suggestion: Suggestion,
    toolbarPosition: ToolbarPosition,
    browserIcons: BrowserIcons,
    onSetTextClicked: (text: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showClipboardSuggestionContent by remember { mutableStateOf(false) }


    WebsiteRow(
        title = if (suggestion.provider is ClipboardProvider) {
            stringResource(R.string.browser_suggest_clipboard_text)
        } else {
            when (suggestion) {
                is Suggestion.SearchSuggestion -> suggestion.text
                is Suggestion.BrandSuggestion -> suggestion.title
                is Suggestion.OpenTabSuggestion -> suggestion.title ?: ""
                is Suggestion.SelectTabSuggestion -> suggestion.title
            }
        },
        subtitle = if (suggestion.provider is ClipboardProvider) {
            if (showClipboardSuggestionContent) {
                when (suggestion) {
                    is Suggestion.SearchSuggestion -> suggestion.text
                    is Suggestion.OpenTabSuggestion -> suggestion.url
                    else -> null
                }
            } else null
        } else {
            when (suggestion) {
                is Suggestion.SearchSuggestion, is Suggestion.BrandSuggestion -> null
                is Suggestion.OpenTabSuggestion -> suggestion.url
                is Suggestion.SelectTabSuggestion -> stringResource(id = R.string.browser_go_to_tab)
            }
        },
        leading = {
            when (suggestion.provider) {
                is ClipboardProvider -> SuggestIcon(R.drawable.icons_paste)
                is MidoriSuggestProvider -> {
                    if (suggestion is Suggestion.SearchSuggestion) {
                        SuggestIcon(R.drawable.icons_search)
                    } else if (suggestion is Suggestion.BrandSuggestion) {
                        suggestion.favicon?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "favicon",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(
                                        RoundedCornerShape(8.dp)
                                    )
                            )
                        } ?: SuggestIcon(R.drawable.icons_search)
                    }
                }
                is DomainProvider -> SuggestIcon(R.drawable.icons_internet)
                is BookmarksStorage ->
                    SuggestUrlIcon(
                        browserIcons = browserIcons,
                        url = (suggestion as Suggestion.OpenTabSuggestion).url,
                        miniature = R.drawable.icons_bookmark
                    )
                is HistoryStorage ->
                    SuggestUrlIcon(
                        browserIcons = browserIcons,
                        url = (suggestion as Suggestion.OpenTabSuggestion).url,
                        miniature = R.drawable.icons_history
                    )
                is SessionTabsProvider ->
                    SuggestUrlIcon(
                        browserIcons = browserIcons,
                        url = (suggestion as Suggestion.SelectTabSuggestion).url,
                        miniature = R.drawable.icons_checkbox_unchecked
                    )
                // "Search history" -> ...
                else -> SuggestIcon(R.drawable.icons_search)
            }
        },
        trailing = {
            if (suggestion.provider is ClipboardProvider) {
                Icon(
                    painter = painterResource(id = when(showClipboardSuggestionContent) {
                        true -> R.drawable.icons_eye_off
                        false -> R.drawable.icons_eye
                    }),
                    contentDescription = when(showClipboardSuggestionContent) {
                        true -> "Hide"
                        false -> "Show"
                    },
                    tint = LocalContentColor.current.copy(0.6f),
                    modifier = Modifier
                        .size(24.dp)
                        .clickable { showClipboardSuggestionContent = !showClipboardSuggestionContent }
                )
            } else {
                when (suggestion) {
                    is Suggestion.OpenTabSuggestion -> {
                        Box(modifier = Modifier.size(24.dp))
                    }

                    is Suggestion.BrandSuggestion -> {
                        // TODO make a separated component for brand suggestion trailing
                        Box {
                            var showInformationPopup by remember { mutableStateOf(false) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable { showInformationPopup = true }
                                    .padding(start = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.browser_brand_suggest_ad),
                                    fontSize = 12.sp,
                                    color = LocalContentColor.current.copy(0.6f)
                                )
                                Icon(
                                    painterResource(id = R.drawable.icons_information),
                                    contentDescription = "information",
                                    tint = LocalContentColor.current.copy(0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Dropdown(
                                expanded = showInformationPopup,
                                onDismissRequest = { showInformationPopup = false },
                                focusable = false,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .sizeIn(maxWidth = 200.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.browser_brand_suggest_information,
                                        suggestion.brand,
                                        suggestion.domain
                                    ),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    else -> {
                        Icon(
                            painter = painterResource(
                                id = if (suggestion is Suggestion.SearchSuggestion) {
                                    when (toolbarPosition) {
                                        ToolbarPosition.TOP -> R.drawable.icons_arrow_backward_up
                                        else -> R.drawable.icons_arrow_backward_down
                                    }
                                } else R.drawable.icons_arrow_tab
                            ),
                            contentDescription = "Go",
                            tint = LocalContentColor.current.copy(0.6f),
                            modifier = Modifier
                                .size(24.dp)
                                .then(
                                    if (suggestion is Suggestion.SearchSuggestion) {
                                        Modifier.clickable { onSetTextClicked(suggestion.text) }
                                    } else Modifier
                                )
                        )
                    }
                }
            }
        },
        highlight = suggestion.search,
        maxTitleLines = if (suggestion is Suggestion.BrandSuggestion) 2 else 1,
        modifier = modifier
    )
}

// TODO Move WebsiteRow to it's own widget file as it is used all over the app
@Composable
fun WebsiteRow(
    title: String?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    highlight: String? = null,
    maxTitleLines: Int = 1
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        leading()

        Column(modifier = Modifier.weight(2f)) {
            Text(
                text = highlight?.let {
                    title?.toSuggestAnnotatedString(it)
                } ?: AnnotatedString(title ?: ""),
                fontSize = 16.sp,
                fontWeight = if (highlight != null) FontWeight.Bold else FontWeight.Normal,
                lineHeight = 20.sp,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.let {
                Text(
                    text = it.toCleanUrl(),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        trailing()
    }
}

@Composable
fun WebsiteRowWithIcon(
    title: String?,
    url: String,
    browserIcons: BrowserIcons,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    WebsiteRow(
        title = title,
        subtitle = url,
        modifier = modifier,
        trailing = trailing,
        leading = {
            UrlIcon(
                browserIcons = browserIcons,
                url = url,
                modifier = Modifier
                    .size(32.dp)
                    .clip(
                        RoundedCornerShape(8.dp)
                    )
            )
        }
    )
}

@Composable
fun SuggestIcon(
    @DrawableRes icon: Int
) {
    Icon(
        painter = painterResource(id = icon),
        contentDescription = "Suggest icon",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(4.dp)
            .size(24.dp)
    )
}

@Composable
fun SuggestUrlIcon(
    browserIcons: BrowserIcons,
    url: String?,
    @DrawableRes miniature: Int
) {
    Box(modifier = Modifier.size(32.dp)) {
        UrlIcon(
            browserIcons = browserIcons,
            url = url,
            modifier = Modifier.clip(RoundedCornerShape(8.dp))
        )
        Icon(
            painter = painterResource(id = miniature),
            contentDescription = "miniature",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomEnd)
                .offset(4.dp, 4.dp)
                .background(MaterialTheme.colorScheme.background, CircleShape)
                .padding(2.dp)
        )
    }
}