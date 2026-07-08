package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import mozilla.components.concept.engine.EngineView

@Composable
fun BoxScope.EngineViewFeatures(
    engineView: EngineView,
    viewModel: BrowserScreenViewModel = hiltViewModel(),
) {
    val canGoBack by viewModel.canGoBack.collectAsState()
    SessionFeature(
        engineView = engineView,
        store = viewModel.store,
        canGoBack = canGoBack,
        goBackUseCase = viewModel.goBack,
        goForwardUseCase = viewModel.goForward,
        closeCurrentTab = viewModel::closeCurrentTab,
        backEnabled = { !viewModel.toolbarState.hasFocus }
    )

    if (viewModel.contentBlockerState.status == ContentBlockerState.Status.ALLOWED) {
        ThumbnailFeature(
            engineView = engineView,
            store = viewModel.store
        )
    }

    FindInPageFeature(
        engineView = engineView,
        store = viewModel.store,
        enabled = { viewModel.showFindInPage },
        onDismiss = { viewModel.updateShowFindInPage(false) },
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
