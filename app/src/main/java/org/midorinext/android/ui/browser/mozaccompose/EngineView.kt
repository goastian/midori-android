package org.midorinext.android.ui.browser.mozaccompose

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.EngineView
import org.mozilla.geckoview.GeckoView


@Composable
fun EngineView(
    engine: Engine,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
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
            val geckoView = remember(it) { findGeckoView(it.asView()) }
            var textureBackendEnabled by remember(it) { mutableStateOf(false) }
            SideEffect {
                if (geckoView != null && !textureBackendEnabled && isFullScreen &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                ) {
                    // Android 16 can retain SurfaceView's old crop during fullscreen resizes.
                    // Keep the backend for this view's lifetime so exiting does not interrupt video.
                    geckoView.setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW)
                    textureBackendEnabled = true
                }
            }
            DisposableEffect(it) {
                org.midorinext.android.diagnostics.ScenarioMetrics.surface(it.asView())
                onDispose { org.midorinext.android.diagnostics.ScenarioMetrics.removeSurface(it.asView()) }
            }
            latestFeatures(it)
        }
    }
}

private fun findGeckoView(view: View): GeckoView? = when (view) {
    is GeckoView -> view
    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findGeckoView(view.getChildAt(it)) }
    else -> null
}
