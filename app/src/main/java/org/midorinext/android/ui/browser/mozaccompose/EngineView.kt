package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineView


@Composable
fun EngineView(
    engine: Engine,
    modifier: Modifier = Modifier,
    features: @Composable BoxScope.(EngineView) -> Unit = {},
) {
    var engineView: EngineView? by remember { mutableStateOf(null) }
    val latestFeatures by rememberUpdatedState(features)

    // GeckoView must receive the exact bounds allocated by Compose. Keeping this explicit avoids
    // a content-sized AndroidView when its caller provides only parent-data (for example weight).
    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.matchParentSize(),
            factory = { context ->
                engine.createView(context).asView().apply {
                    this.isNestedScrollingEnabled = true
                }
            },
            update = { view ->
                engineView = view as EngineView
            }
        )

        engineView?.let {
            latestFeatures(it)
        }
    }
}
