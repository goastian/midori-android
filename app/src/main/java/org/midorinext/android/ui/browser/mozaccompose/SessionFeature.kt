package org.midorinext.android.ui.browser.mozaccompose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.session.SessionFeature
import mozilla.components.feature.session.SessionUseCases

@Composable
fun SessionFeature(
    engineView: EngineView,
    store: BrowserStore,
    canGoBack: Boolean,
    goBackUseCase: SessionUseCases.GoBackUseCase,
    goForwardUseCase: SessionUseCases.GoForwardUseCase,
    closeCurrentTab: () -> Unit,
    backEnabled: () -> Boolean = { true }
) {
    val feature = remember(engineView) {
        SessionFeature(
            store = store,
            goBackUseCase = goBackUseCase,
            goForwardUseCase = goForwardUseCase,
            engineView = engineView
        )
    }

    ComposeFeatureWrapper(feature = feature) {
        if (backEnabled()) {
            if (engineView.canClearSelection()) {
                BackHandler(true) { engineView.clearSelection() }
            } else if (canGoBack) {
                BackHandler(true) { goBackUseCase() }
            } else {
                BackHandler(true) { closeCurrentTab() }
            }
        }
    }
}
