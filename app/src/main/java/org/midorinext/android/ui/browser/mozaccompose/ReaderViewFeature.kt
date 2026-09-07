package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.readerview.view.ReaderViewControlsBar

/**
 * Creates Mozilla's built-in Reader View feature for the browser surface.
 *
 * The feature owns the WebExtension that detects readable pages, caches the current article and
 * renders the simplified reader document. Its configuration automatically follows the app/system
 * light or dark theme the first time it is used and persists subsequent reader preferences.
 */
@Composable
fun rememberReaderViewFeature(
    store: BrowserStore,
    engine: Engine,
): ReaderViewFeature {
    val context = LocalContext.current
    val controlsView = remember(context) { ReaderViewControlsBar(context) }

    return remember(context, store, engine, controlsView) {
        ReaderViewFeature(
            context = context,
            engine = engine,
            store = store,
            controlsView = controlsView,
        )
    }
}
