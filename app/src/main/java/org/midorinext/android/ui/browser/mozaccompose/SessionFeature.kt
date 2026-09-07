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

    DisposableEffect(feature) {
        feature.start()
        onDispose {
            feature.stop()
        }
    }

    if (backEnabled()) {
        if (engineView.canClearSelection()) {
            BackHandler(true) { engineView.clearSelection() }
        } else if (canGoBack) {
            BackHandler(true) { goBackUseCase() }
        }
        // At the root of the selected page there is deliberately no handler: Android's activity
        // dispatcher owns Back, including predictive Back. Closing a tab remains an explicit tab
        // tray action and can no longer trap users by closing and immediately recreating the last
        // normal tab.
    }
}
