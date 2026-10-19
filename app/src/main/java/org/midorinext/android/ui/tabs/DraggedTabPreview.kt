package org.midorinext.android.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.newtab.isNativeHomeUrl

@Composable
internal fun DraggedTabPreview(drag: TabDragState, tab: TabSessionState, storage: ThumbnailStorage, viewport: Rect) {
    val source = drag.sourceBounds ?: return
    val initial = drag.initialPosition ?: return
    val density = LocalDensity.current
    val scale = 0.84f
    val title = if (isNativeHomeUrl(tab.content.url)) stringResource(R.string.browser_new_tab)
        else tab.content.title.ifBlank { tab.content.url.toCleanHost() }
    Surface(
        modifier = Modifier.offset {
            val pointer = drag.position ?: initial
            IntOffset(
                (pointer.x - (initial.x - source.left) * scale - viewport.left).toInt(),
                (pointer.y - (initial.y - source.top) * scale - viewport.top).toInt(),
            )
        }.size(with(density) { (source.width * scale).toDp() }, with(density) { (source.height * scale).toDp() })
            .testTag("tab_drag_preview"),
        shape = RoundedCornerShape(16.dp), shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column {
            Text(title, Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            TabPreview(tab, storage, Modifier.fillMaxWidth().weight(1f))
        }
    }
}
