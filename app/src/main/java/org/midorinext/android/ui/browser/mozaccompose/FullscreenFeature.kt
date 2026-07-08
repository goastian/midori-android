package org.midorinext.android.ui.browser.mozaccompose

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.ext.activity
import org.midorinext.android.ui.browser.toolbar.BrowserToolbarState
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.FullScreenFeature
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.support.ktx.android.view.enterImmersiveMode
import mozilla.components.support.ktx.android.view.exitImmersiveMode

@Composable
fun FullScreenFeature(
    store: BrowserStore,
    toolbarState: BrowserToolbarState,
    sessionUseCases: SessionUseCases
) {
    val activity = LocalContext.current.activity
    var fullScreenEnabled by remember { mutableStateOf(store.state.selectedTab?.content?.fullScreen ?: false) }

    val fullScreenChanged: (Boolean) -> Unit = { enabled ->
        fullScreenEnabled = enabled
        if (enabled) {
            activity?.enterImmersiveMode()
            toolbarState.updateVisibility(false)
        } else {
            activity?.exitImmersiveMode()
            toolbarState.updateVisibility(true)
        }
    }

    val viewportFitChanged: (Int) -> Unit = { viewportFit ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity?.let {
                it.window.attributes.layoutInDisplayCutoutMode = viewportFit
            }
        }
    }

    val fullScreenFeature = remember(viewportFitChanged, fullScreenChanged) {
        FullScreenFeature(
            store,
            sessionUseCases,
            viewportFitChanged = viewportFitChanged,
            fullScreenChanged = fullScreenChanged
        )
    }

    ComposeFeatureWrapper(feature = fullScreenFeature)

    BackHandler(fullScreenEnabled) {
        sessionUseCases.exitFullscreen()
    }
}