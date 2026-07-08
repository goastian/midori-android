package org.midorinext.android.contentBlocker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.midorinext.android.ext.isMidoriUrl
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.permission.PermissionRequest
import kotlin.text.startsWith

class ContentBlockerObserver(
    private val contentBlockerState: ContentBlockerState
): EngineSession.Observer {
    override fun onLocationChange(url: String) {
        if (url.isMidoriUrl() || url.startsWith("https://astian.org") || url.startsWith("https://astian.org/community")) {
            contentBlockerState.onMidori()
        } else {
            contentBlockerState.check(url)
        }
    }

    override fun onAppPermissionRequest(permissionRequest: PermissionRequest) = Unit
    override fun onContentPermissionRequest(permissionRequest: PermissionRequest) = Unit
}

@Composable
fun ContentBlockerObserver(
    contentBlockerState: ContentBlockerState,
    session: EngineSession?
) {
    val observer = remember { ContentBlockerObserver(contentBlockerState) }
    DisposableEffect(session) {
        session?.register(observer)
        onDispose {
            contentBlockerState.cancel()
            session?.unregister(observer)
        }
    }
}