package org.midorinext.android.ui.browser.toolbar

import androidx.compose.animation.*
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.ui.browser.suggest.Suggest
import mozilla.components.browser.icons.BrowserIcons

@Composable
fun Toolbar(
    onTextCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    toolbarState: BrowserToolbarState,
    browserIcons: BrowserIcons,
    beforeTextField: @Composable () -> Unit = {},
    beforeTextFieldVisible: () -> Boolean = { true },
    afterTextField: @Composable () -> Unit = {},
    afterTextFieldVisible: () -> Boolean = { true },
    animationDurationMs: Int = 200,
    animationEasing: Easing = FastOutSlowInEasing,
    onMidoriIconClicked: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {}
) {
    val toolbarPosition by toolbarState.toolbarPosition.collectAsState()
    val loadingProgress by toolbarState.loadingProgress.collectAsState()

    // TODO move suggestShown variable to viewmodel
    //  or handle dividers differently
    //  or pass down this values to ToolbarSuggest which does the same again
    //  as it's expensive here for only handling toolbar divider visibility
    val suggestions by toolbarState.suggestions.collectAsState()
    val suggestShown = remember(toolbarState.hasFocus, suggestions) {
        toolbarState.hasFocus && suggestions.any { it.value.isNotEmpty() }
    }

    val toolbarTextFieldPaddingStart by animateDpAsState(
        targetValue = if (!beforeTextFieldVisible() || toolbarState.hasFocus) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = animationDurationMs, easing = animationEasing),
        label = "toolbarTextFieldPaddingStart"
    )

    // TODO move commitSuggestion to viewModel, and transmit it as param
    val commitSuggestion: (Suggestion) -> Unit = { suggestion ->
        when (suggestion) {
            is Suggestion.SelectTabSuggestion -> onTextCommit(suggestion.url)
            is Suggestion.SearchSuggestion -> onTextCommit(suggestion.text) // TODO toolbarState.updateText(suggestion.text), but it looses focus on the toolbar, which causes issues.
            is Suggestion.BrandSuggestion -> {
                toolbarState.datahub.brandSuggestClicked(suggestion) // TODO Move this inside viewmodel, with whole commitSuggestion
                onTextCommit(suggestion.url)
            }
            is Suggestion.OpenTabSuggestion -> (suggestion.url ?: suggestion.title)?.let {
                onTextCommit(it)
            }
        }
    }

    Column(modifier = modifier) {
        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            ToolbarSuggest(
                toolbarState = toolbarState,
                commitSuggestion = commitSuggestion,
                browserIcons = browserIcons,
                modifier = Modifier.weight(2f)
            )
            ToolbarProgressBar(toolbarState = toolbarState)
            if (!suggestShown && loadingProgress == 1f) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            }
        }

        Row(modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { toolbarState.updateFocus(true) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    val (dx, dy) = dragAmount
                    // Check for vertical swipe (up or down)
                    if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                        if (dy < -50f) { // Swipe up threshold
                            onSwipeUp()
                        } else if (dy > 50f) { // Swipe down threshold
                            onSwipeDown()
                        }
                    }
                }
            }
        ) {
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                AnimatedVisibility(
                    visible = beforeTextFieldVisible(),
                    enter = expandHorizontally(animationSpec = tween(durationMillis = animationDurationMs, easing = animationEasing)),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start, animationSpec = tween(durationMillis = animationDurationMs, easing = animationEasing))
                ) {
                    beforeTextField()
                }

                ToolbarInput(
                    toolbarState = toolbarState,
                    onCommit = onTextCommit,
                    modifier = Modifier
                        .weight(2f, true)
                        .padding(
                            start = toolbarTextFieldPaddingStart,
                            end = 8.dp, // toolbarTextFieldPaddingEnd,
                            top = 8.dp,
                            bottom = 8.dp
                        ),
                    onMidoriIconClicked = onMidoriIconClicked
                )

                AnimatedVisibility(
                    visible = afterTextFieldVisible(),
                    enter = expandHorizontally(animationSpec = tween(durationMillis = animationDurationMs, easing = animationEasing)),
                    exit = shrinkHorizontally(animationSpec = tween(durationMillis = animationDurationMs, easing = animationEasing))
                ) {
                    afterTextField()
                }
            }
        }

        if (toolbarPosition == ToolbarPosition.TOP) {
            if (!suggestShown && loadingProgress == 1f) {
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            }
            ToolbarProgressBar(toolbarState = toolbarState)
            ToolbarSuggest(
                toolbarState = toolbarState,
                commitSuggestion = commitSuggestion,
                browserIcons = browserIcons,
                modifier = Modifier.weight(2f)
            )
        }
    }
}

@Composable
private fun ToolbarProgressBar(
    toolbarState: BrowserToolbarState
) {
    val loadingProgress by toolbarState.loadingProgress.collectAsState()

    ProgressBar(
        loadingProgress = loadingProgress,
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
    )
}

@Composable
private fun ToolbarSuggest(
    toolbarState: BrowserToolbarState,
    browserIcons: BrowserIcons,
    commitSuggestion: (Suggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val toolbarPosition by toolbarState.toolbarPosition.collectAsState()
    val suggestions by toolbarState.suggestions.collectAsState()

    if (toolbarState.hasFocus && suggestions.any { it.value.isNotEmpty() }) {
        Suggest(
            suggestions = suggestions,
            onSuggestionClicked = { suggestion ->
                commitSuggestion(suggestion)
                toolbarState.updateFocus(false)
            },
            onSetTextClicked = {
                toolbarState.updateText(
                    TextFieldValue(it, selection = TextRange(it.length))
                )
            },
            toolbarPosition = toolbarPosition,
            browserIcons = browserIcons,
            modifier = modifier.background(Color.White)
        )
    }
}
