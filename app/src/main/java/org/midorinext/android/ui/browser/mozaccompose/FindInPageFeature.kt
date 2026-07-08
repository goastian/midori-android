package org.midorinext.android.ui.browser.mozaccompose

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.lib.state.ext.observeAsComposableState

@Composable
fun FindInPageFeature(
    engineView: EngineView,
    store: BrowserStore,
    enabled: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val session by store.observeAsComposableState { state ->
        state.selectedTab?.engineState?.engineSession
    }
    val findResultState by store.observeAsComposableState { state ->
        state.selectedTab?.content?.findResults?.lastOrNull()
    }

    var searchText by remember { mutableStateOf("") }
    val dismiss: () -> Unit = {
        onDismiss()
        searchText = ""
        session?.clearFindMatches()
    }

    LaunchedEffect(enabled, density) {
        if (enabled()) {
            engineView.setDynamicToolbarMaxHeight(with(density) { 56.dp.roundToPx() })
        }
    }

    BackHandler(enabled()) {
        dismiss()
    }

    AnimatedVisibility(visible = enabled(), modifier = modifier) {
        // val focusManager = LocalFocusManager.current
        val focusRequester = remember { FocusRequester() }

        LaunchedEffect(true) {
            focusRequester.requestFocus()
        }

        TextField(
            value = searchText,
            onValueChange = { s: String ->
                searchText = s
                session?.findAll(searchText)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    findResultState?.let {
                        val ordinal =
                            if (it.numberOfMatches > 0) it.activeMatchOrdinal + 1
                            else it.activeMatchOrdinal
                        Text(text = "$ordinal/${it.numberOfMatches}")

                        IconButton(onClick = {
                            session?.findNext(forward = false)
                            // engineView.asView().clearFocus()
                            // focusManager.clearFocus()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.icons_arrow_up),
                                contentDescription = "previous"
                            )
                        }
                        IconButton(onClick = {
                            session?.findNext(forward = true)
                            // TODO ask mozilla what the engine.clearFocus is for in findInPage
                            // engineView.asView().clearFocus()
                            // focusManager.clearFocus()
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.icons_arrow_down),
                                contentDescription = "next"
                            )
                        }
                    }
                    IconButton(onClick = { dismiss() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.icons_close),
                            contentDescription = "close"
                        )
                    }
                }
            },
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .focusRequester(focusRequester)
        )
    }
}