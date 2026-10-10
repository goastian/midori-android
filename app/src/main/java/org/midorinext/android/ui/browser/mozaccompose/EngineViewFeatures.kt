package org.midorinext.android.ui.browser.mozaccompose

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.ui.browser.BrowserScreenViewModel
import mozilla.components.concept.engine.EngineView

@Composable
fun BoxScope.EngineViewFeatures(
    engineView: EngineView,
    viewModel: BrowserScreenViewModel = hiltViewModel(),
) {
    val canGoBack by viewModel.canGoBackInPage.collectAsStateWithLifecycle()
    SessionFeature(
        engineView = engineView,
        store = viewModel.store,
        canGoBack = canGoBack,
        goBackUseCase = viewModel.sessionUseCases.goBack,
        goForwardUseCase = viewModel.goForward,
        backEnabled = { !viewModel.toolbarState.hasFocus }
    )

    ThumbnailFeature(
        engineView = engineView,
        store = viewModel.store
    )

    FindInPageFeature(
        engineView = engineView,
        store = viewModel.store,
        enabled = { viewModel.showFindInPage },
        onDismiss = { viewModel.updateShowFindInPage(false) },
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
