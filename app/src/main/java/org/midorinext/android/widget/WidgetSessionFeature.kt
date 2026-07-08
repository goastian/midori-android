package org.midorinext.android.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.EngineView

@Composable
fun WidgetSessionFeature(
    engineView: EngineView,
    session: EngineSession
) {
    LaunchedEffect(engineView, session) {
        engineView.release()
        engineView.render(session)
    }
}