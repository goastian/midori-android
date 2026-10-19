package org.midorinext.android.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.newtab.isNativeHomeUrl
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ui.animation.reduceMotionRequested

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TabRow(
    tab: TabSessionState,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    onSelected: (tab: TabSessionState) -> Unit,
    onDeleted: (tab: TabSessionState) -> Unit,
    selectionMode: Boolean = false,
    isSelectedForGrouping: Boolean = false,
    groupId: String? = null,
    onRemoveFromGroup: (String, String) -> Unit = { _, _ -> },
    onLongPressed: (TabSessionState) -> Unit = {},
    dragEnabled: Boolean = false,
    dragGestureAtParent: Boolean = false,
    isBeingDragged: Boolean = false,
    onDragStarted: (TabSessionState) -> Unit = {},
    onDragPositionChanged: (Offset) -> Unit = {},
    onDragFinished: () -> Unit = {},
    onDragCancelled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isPrivateBrowsingHome = tab.content.private && (tab.content.url.isBlank() || isNativeHomeUrl(tab.content.url))
    val isNativeHome = isNativeHomeUrl(tab.content.url)
    val domain = remember(tab.content.url) { tab.content.url.toCleanHost() }
    val highlighted = if (selectionMode) isSelectedForGrouping else selected
    val animationsEnabled = !reduceMotionRequested()
    val dismissal = rememberTabDismissal(tab.id) { onDeleted(tab) }
    val surfaceColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(if (animationsEnabled) 140 else 0), label = "tab row surface",
    )
    val borderColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(if (animationsEnabled) 140 else 0), label = "tab row outline",
    )
    val shape = RoundedCornerShape(14.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .testTag("tab_row:${tab.id}")
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .heightIn(min = 88.dp)
            .graphicsLayer {
                val progress = dismissal.progress.value
                val offset = dismissal.dragOffset
                translationX = offset + if (animationsEnabled) ((if (offset < 0) -size.width else size.width) - offset) * progress else 0f
                alpha = (1f - progress) * if (isBeingDragged) 0f else 1f
            }
            .tabSwipeToDismiss(dismissal, enabled = !selectionMode && !isBeingDragged && !dismissal.requested)
            .semantics { this.selected = highlighted }
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = borderColor,
                shape = shape
            )
            .clip(shape)
            .background(surfaceColor)
            .then(
                if (dragEnabled) {
                    // Keep the long press available to the drag detector; combinedClickable would
                    // consume it and leave the tab selected but unable to be dropped on a group.
                    Modifier.clickable(enabled = !dismissal.requested, onClick = { onSelected(tab) })
                } else {
                    Modifier.combinedClickable(
                        enabled = !dismissal.requested,
                        onClick = { onSelected(tab) },
                        onLongClick = { onLongPressed(tab) }
                    )
                }
            )
            .then(
                if (dragEnabled && !dragGestureAtParent) {
                    Modifier.tabDragGestures(
                        id = tab.id,
                        onStart = { onDragStarted(tab) },
                        onPosition = onDragPositionChanged,
                        onEnd = onDragFinished,
                        onCancel = onDragCancelled,
                    )
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
                .width(56.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            TabPreview(tab, thumbnailStorage, Modifier.fillMaxSize())
        }

        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
            Text(
                text = if (isPrivateBrowsingHome) stringResource(id = R.string.browser_new_tab_private)
                        else if (isNativeHome) stringResource(R.string.browser_new_tab)
                        else tab.content.title.ifBlank { domain },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (domain.isNotBlank()) {
                Text(
                    text = domain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (selectionMode) {
            Checkbox(
                checked = isSelectedForGrouping,
                onCheckedChange = { onSelected(tab) }
            )
        } else if (groupId != null) {
            IconButton(
                onClick = { onRemoveFromGroup(groupId, tab.id) },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_folder),
                    contentDescription = stringResource(R.string.browser_remove_tab_from_group)
                )
            }
        } else {
            IconButton(
                onClick = dismissal::dismiss,
                enabled = !dismissal.requested,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_close),
                    contentDescription = stringResource(R.string.tab_tray_close_tab),
                )
            }
        }
    }
}
