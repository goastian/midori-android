package org.midorinext.android.ui.browser.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
import mozilla.components.browser.icons.BrowserIcons
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import org.midorinext.android.ui.browser.suggest.Suggest

internal val ToolbarHeight = 64.dp
private val AddressBarHeight = 48.dp

/**
 * Firefox-style browser toolbar with distinct display and edit layouts.
 *
 * Display mode is a compact toolbar over the web content. Edit mode becomes a full-screen search
 * surface, keeping the address field attached to the selected top or bottom edge.
 */
@Composable
fun Toolbar(
    onTextCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    toolbarState: BrowserToolbarState,
    browserIcons: BrowserIcons,
    beforeTextField: @Composable () -> Unit = {},
    beforeTextFieldVisible: () -> Boolean = { true },
    pageEndAction: @Composable () -> Unit = {},
    pageEndActionVisible: () -> Boolean = { false },
    afterTextField: @Composable () -> Unit = {},
    afterTextFieldVisible: () -> Boolean = { true },
    onMidoriIconClicked: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
) {
    val toolbarPosition by toolbarState.toolbarPosition.collectAsStateWithLifecycle()
    val loadingProgress by toolbarState.loadingProgress.collectAsStateWithLifecycle()
    val suggestions by toolbarState.suggestions.collectAsStateWithLifecycle()
    val recentSearches by toolbarState.recentSearches.collectAsStateWithLifecycle()
    val isEditMode = toolbarState.hasFocus

    val commitSuggestion: (Suggestion) -> Unit = { suggestion ->
        when (suggestion) {
            is Suggestion.SelectTabSuggestion -> onTextCommit(suggestion.url)
            is Suggestion.SearchSuggestion -> onTextCommit(suggestion.text)
            is Suggestion.BrandSuggestion -> {
                toolbarState.datahub.brandSuggestClicked(suggestion)
                onTextCommit(suggestion.url)
            }
            is Suggestion.OpenTabSuggestion -> (suggestion.url ?: suggestion.title)?.let(onTextCommit)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
    ) {
        if (toolbarPosition == ToolbarPosition.BOTTOM && isEditMode) {
            ToolbarSuggest(
                toolbarState = toolbarState,
                toolbarPosition = toolbarPosition,
                suggestions = suggestions,
                recentSearches = recentSearches,
                commitSuggestion = commitSuggestion,
                commitRecentSearch = onTextCommit,
                browserIcons = browserIcons,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }

        ToolbarChrome(
            toolbarState = toolbarState,
            toolbarPosition = toolbarPosition,
            loadingProgress = loadingProgress,
            isEditMode = isEditMode,
            beforeTextField = beforeTextField,
            beforeTextFieldVisible = beforeTextFieldVisible,
            pageEndAction = pageEndAction,
            pageEndActionVisible = pageEndActionVisible,
            afterTextField = afterTextField,
            afterTextFieldVisible = afterTextFieldVisible,
            onTextCommit = onTextCommit,
            onMidoriIconClicked = onMidoriIconClicked,
            onSwipeUp = onSwipeUp,
            onSwipeDown = onSwipeDown,
            onSwipeLeft = onSwipeLeft,
            onSwipeRight = onSwipeRight,
        )

        if (toolbarPosition == ToolbarPosition.TOP && isEditMode) {
            ToolbarSuggest(
                toolbarState = toolbarState,
                toolbarPosition = toolbarPosition,
                suggestions = suggestions,
                recentSearches = recentSearches,
                commitSuggestion = commitSuggestion,
                commitRecentSearch = onTextCommit,
                browserIcons = browserIcons,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun ToolbarChrome(
    toolbarState: BrowserToolbarState,
    toolbarPosition: ToolbarPosition,
    loadingProgress: Float,
    isEditMode: Boolean,
    beforeTextField: @Composable () -> Unit,
    beforeTextFieldVisible: () -> Boolean,
    pageEndAction: @Composable () -> Unit,
    pageEndActionVisible: () -> Boolean,
    afterTextField: @Composable () -> Unit,
    afterTextFieldVisible: () -> Boolean,
    onTextCommit: (String) -> Unit,
    onMidoriIconClicked: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ToolbarHeight)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .toolbarGestures(
                    enabled = !isEditMode,
                    onSwipeUp = onSwipeUp,
                    onSwipeDown = onSwipeDown,
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                ToolbarInput(
                    toolbarState = toolbarState,
                    onCommit = onTextCommit,
                    modifier = Modifier
                        .weight(1f)
                        .height(AddressBarHeight)
                        .padding(
                            start = 8.dp,
                            end = if (isEditMode) 8.dp else 0.dp,
                        ),
                    leadingContent = when {
                        isEditMode -> ({ ToolbarSearchSelector(toolbarState) })
                        beforeTextFieldVisible() -> beforeTextField
                        else -> null
                    },
                    trailingContent = if (!isEditMode && pageEndActionVisible()) {
                        pageEndAction
                    } else {
                        null
                    },
                    onMidoriIconClicked = onMidoriIconClicked,
                )

                if (!isEditMode && afterTextFieldVisible()) {
                    afterTextField()
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.align(
                if (toolbarPosition == ToolbarPosition.BOTTOM) {
                    Alignment.TopCenter
                } else {
                    Alignment.BottomCenter
                }
            ),
            color = MaterialTheme.colorScheme.outline,
        )

        if (!isEditMode && loadingProgress in 0f..0.999f && loadingProgress > 0f) {
            LinearProgressIndicator(
                progress = { loadingProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(
                        if (toolbarPosition == ToolbarPosition.BOTTOM) {
                            Alignment.TopCenter
                        } else {
                            Alignment.BottomCenter
                        }
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}

private fun Modifier.toolbarGestures(
    enabled: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(onSwipeUp, onSwipeDown, onSwipeLeft, onSwipeRight) {
        var totalDragX = 0f
        var totalDragY = 0f
        detectDragGestures(
            onDragStart = {
                totalDragX = 0f
                totalDragY = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
            },
            onDragEnd = {
                val threshold = 72.dp.toPx()
                if (abs(totalDragY) > abs(totalDragX)) {
                    when {
                        totalDragY <= -threshold -> onSwipeUp()
                        totalDragY >= threshold -> onSwipeDown()
                    }
                } else {
                    when {
                        totalDragX <= -threshold -> onSwipeLeft()
                        totalDragX >= threshold -> onSwipeRight()
                    }
                }
            },
        )
    }
}

@Composable
private fun ToolbarSuggest(
    toolbarState: BrowserToolbarState,
    toolbarPosition: ToolbarPosition,
    suggestions: Map<SuggestionProvider, List<Suggestion>>,
    recentSearches: List<String>,
    browserIcons: BrowserIcons,
    commitSuggestion: (Suggestion) -> Unit,
    commitRecentSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showRecentSearches = toolbarState.isQueryPrefilled || toolbarState.text.text.isBlank()
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { toolbarState.updateFocus(false) },
    ) {
        Suggest(
            suggestions = suggestions,
            onSuggestionClicked = { suggestion ->
                commitSuggestion(suggestion)
                toolbarState.updateFocus(false)
            },
            onSetTextClicked = {
                toolbarState.updateTextFromUser(
                    TextFieldValue(it, selection = TextRange(it.length))
                )
            },
            toolbarPosition = toolbarPosition,
            browserIcons = browserIcons,
            recentSearches = recentSearches,
            showRecentSearches = showRecentSearches,
            onRecentSearchClicked = { search ->
                commitRecentSearch(search)
                toolbarState.updateFocus(false)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
