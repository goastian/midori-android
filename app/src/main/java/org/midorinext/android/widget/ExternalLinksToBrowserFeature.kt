package org.midorinext.android.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.midorinext.android.ext.activity
import org.midorinext.android.ext.isMidoriUrl
import mozilla.components.concept.engine.EngineSession

@Composable
fun ExternalLinksToBrowserFeature(session: EngineSession) {
    val activity = LocalContext.current.activity as WidgetActivity
    val observer = remember { object: EngineSession.Observer {
        override fun onLocationChange(url: String, hasUserGesture: Boolean) {
            if (url.isNotEmpty() && url != "about:blank" && !url.isMidoriUrl()) {
                session.goBack()
                activity.openFullBrowsingActivity(url)
            }
        }
    } }

    DisposableEffect(session) {
        session.register(observer)
        onDispose {
            session.unregister(observer)
        }
    }
}