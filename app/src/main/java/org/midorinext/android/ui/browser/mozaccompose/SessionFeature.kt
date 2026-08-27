package org.midorinext.android.ui.browser.mozaccompose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.feature.media.ext.findActiveMediaTab
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

    DisposableEffect(feature) {
        feature.start()
        onDispose {
            // Releasing the EngineView tears down Gecko's display and stops the decoder. Keep
            // the rendering session attached while user-initiated media is playing; the media
            // service can then continue without a pause when the tab tray replaces the UI.
            val isPlaying = store.state.findActiveMediaTab()
                ?.mediaSessionState
                ?.playbackState == MediaSession.PlaybackState.PLAYING
            if (!isPlaying) {
                feature.stop()
            }
        }
    }

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
