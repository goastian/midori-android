package org.midorinext.android.ui.browser.mozaccompose

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.media.fullscreen.MediaSessionFullscreenFeature
import mozilla.components.feature.session.ScreenOrientationFeature
import org.midorinext.android.ext.activity

@Composable
fun MediaFullscreenFeature(engine: Engine, store: BrowserStore) {
    val activity = LocalContext.current.activity ?: return
    val orientation = remember(engine, activity) { ScreenOrientationFeature(engine, activity) }
    val media = remember(store, activity) { MediaSessionFullscreenFeature(activity, store, null) }

    LifecycleStartEffect(orientation, media) {
        orientation.start()
        media.start()
        onStopOrDispose {
            media.stop()
            orientation.stop()
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
