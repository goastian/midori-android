package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.preferences.app.Appearance
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.readerview.view.ReaderViewControlsBar

/**
 * Creates Mozilla's built-in Reader View feature for the browser surface.
 *
 * The feature owns the WebExtension that detects readable pages, caches the current article and
 * renders the simplified reader document. Explicit app themes take precedence without replacing
 * the user's saved reader preferences; system appearance leaves the reader's own theme in control.
 */
@Composable
fun rememberReaderViewFeature(
    store: BrowserStore,
    engine: Engine,
    appearance: Appearance,
): ReaderViewFeature {
    val context = LocalContext.current
    val controlsView = remember(context) { ReaderViewControlsBar(context) }

    val feature = remember(context, store, engine, controlsView) {
        ReaderViewFeature(
            context = context,
            engine = engine,
            store = store,
            controlsView = controlsView,
        )
    }
    SideEffect {
        feature.colorSchemeOverride = when (appearance) {
            Appearance.LIGHT -> ReaderViewFeature.ColorScheme.LIGHT
            Appearance.DARK -> ReaderViewFeature.ColorScheme.DARK
            else -> null
        }
    }
    return feature
}
