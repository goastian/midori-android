package org.midorinext.android.widget.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.ext.isValidUrl
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.ui.browser.mozaccompose.EngineView
import org.midorinext.android.ui.browser.suggest.Suggest
import org.midorinext.android.ui.browser.toolbar.KeyboardObserver
import org.midorinext.android.ui.browser.toolbar.ToolbarInput
import org.midorinext.android.widget.ExternalLinksToBrowserFeature
import org.midorinext.android.widget.WidgetActivity
import org.midorinext.android.widget.WidgetSessionFeature
import org.midorinext.android.widget.WidgetViewModel

@Composable
fun Widget(
    viewmodel: WidgetViewModel = hiltViewModel()
) {
    val activity = LocalContext.current.activity as WidgetActivity

    LaunchedEffect(Unit) {
        viewmodel.toolbarState.updateFocus(true)
    }

    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Scaffold(modifier = Modifier.imePadding()) { padding ->
            Column(Modifier.padding(padding)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                ) {
                    ToolbarInput(
                        toolbarState = viewmodel.toolbarState,
                        onCommit = { viewmodel.commitSearch() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.weight(2f)) {
                    if (viewmodel.currentSearch != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outline
                            )

                            EngineView(
                                engine = viewmodel.engine,
                                modifier = Modifier.weight(2f)
                            ) { engineView ->
                                WidgetSessionFeature(
                                    engineView = engineView,
                                    session = viewmodel.session
                                )
                            }
                        }
                    }

                    val suggestions by viewmodel.toolbarState.suggestions.collectAsState()
                    if (viewmodel.toolbarState.hasFocus && viewmodel.toolbarState.text.text.isNotEmpty() && suggestions.isNotEmpty()) {
                        Suggest(
                            suggestions = suggestions,
                            onSuggestionClicked = { suggestion ->
                                when (suggestion) {
                                    is Suggestion.SearchSuggestion -> {
                                        if (suggestion.text.isValidUrl() && !suggestion.text.isMidoriUrl()) {
                                            activity.openFullBrowsingActivity(suggestion.text)
                                        } else {
                                            viewmodel.toolbarState.updateText(suggestion.text)
                                            viewmodel.toolbarState.updateFocus(false)
                                            viewmodel.commitSearch()
                                        }
                                    }

                                    is Suggestion.OpenTabSuggestion -> { // For URLs from clipboard provider
                                        suggestion.url?.let {
                                            activity.openFullBrowsingActivity(it)
                                        }
                                    }

                                    is Suggestion.BrandSuggestion -> {
                                        viewmodel.datahub.brandSuggestClicked(suggestion)
                                        activity.openFullBrowsingActivity(suggestion.url)
                                    }

                                    else -> throw UnsupportedOperationException("Not a valid suggestion for widget")
                                }
                            },
                            onSetTextClicked = { viewmodel.toolbarState.updateText(it) },
                            toolbarPosition = ToolbarPosition.TOP,
                            browserIcons = viewmodel.toolbarState.browserIcons,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    KeyboardObserver(toolbarState = viewmodel.toolbarState)
    ExternalLinksToBrowserFeature(viewmodel.session)

    BackHandler(enabled = viewmodel.currentSearch != null) {
        viewmodel.resetSearch()
    }
}