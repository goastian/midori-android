package org.midorinext.android.ui.browser.mozaccompose

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.BrowserThumbnails
import mozilla.components.concept.engine.EngineView

@Composable
fun ThumbnailFeature(
    engineView: EngineView,
    store: BrowserStore
) {
    val context = LocalContext.current
    val feature = remember(context, engineView) {
        BrowserThumbnails(context, engineView, store)
    }
    ComposeFeatureWrapper(feature = feature)
}