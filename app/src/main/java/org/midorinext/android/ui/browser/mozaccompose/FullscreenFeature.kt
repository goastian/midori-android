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
    var fullScreenEnabled by remember(store) { mutableStateOf(store.state.selectedTab?.content?.fullScreen ?: false) }
    val fullScreenFeature = remember(store, sessionUseCases, activity, toolbarState) {
        FullScreenFeature(
            store,
            sessionUseCases,
            viewportFitChanged = { viewportFit ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    activity?.window?.let { window ->
                        window.attributes = window.attributes.apply {
                            layoutInDisplayCutoutMode = viewportFit
                        }
                    }
                }
            },
            fullScreenChanged = { enabled ->
                fullScreenEnabled = enabled
                if (enabled) {
                    toolbarState.updateFocus(false)
                    activity?.forceHideKeyboard()
                    activity?.enterImmersiveMode()
                } else {
                    activity?.exitImmersiveMode()
                }
                toolbarState.updateVisibility(!enabled)
            },
        )
    }

    DisposableEffect(fullScreenFeature) {
        fullScreenFeature.start()
        onDispose {
            fullScreenFeature.stop()
            if (fullScreenEnabled) {
                activity?.exitImmersiveMode()
                toolbarState.updateVisibility(true)
            }
        }
    }

    BackHandler(fullScreenEnabled) {
        sessionUseCases.exitFullscreen()
    }
}
