package org.midorinext.android.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ui.browser.home.HomePrivateBrowsingContent
import org.midorinext.android.ui.theme.LocalMidoriTheme

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TabRow(
    tab: TabSessionState,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    onSelected: (tab: TabSessionState) -> Unit,
    onDeleted: (tab: TabSessionState) -> Unit,
    contentBlockerState: ContentBlockerState,
    selectionMode: Boolean = false,
    isSelectedForGrouping: Boolean = false,
    groupId: String? = null,
    onRemoveFromGroup: (String, String) -> Unit = { _, _ -> },
    onLongPressed: (TabSessionState) -> Unit = {},
    dragEnabled: Boolean = false,
    isBeingDragged: Boolean = false,
    onDragStarted: (TabSessionState) -> Unit = {},
    onDragPositionChanged: (Offset) -> Unit = {},
    onDragFinished: () -> Unit = {},
    onDragCancelled: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isTabBlocked = contentBlockerState.getStatusForTab(tab.id) != ContentBlockerState.Status.ALLOWED
    val isPrivateBrowsingHome = LocalMidoriTheme.current.private && tab.content.url == ""
    val coordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isBeingDragged) 0.42f else 1f)
            .onGloballyPositioned { coordinates.value = it }
            .background(
                if (selected || isSelectedForGrouping) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .then(
                if (dragEnabled) {
                    // Keep the long press available to the drag detector; combinedClickable would
                    // consume it and leave the tab selected but unable to be dropped on a group.
                    Modifier.clickable(onClick = { onSelected(tab) })
                } else {
                    Modifier.combinedClickable(
                        onClick = { onSelected(tab) },
                        onLongClick = { onLongPressed(tab) }
                    )
                }
            )
            .then(
                if (dragEnabled) {
                    Modifier.pointerInput(tab.id, selectionMode) {
                        val reportDragPosition: (Offset) -> Unit = { position ->
                            coordinates.value?.boundsInWindow()?.let { bounds ->
                                onDragPositionChanged(
                                    Offset(bounds.left + position.x, bounds.top + position.y)
                                )
                            }
                        }
                        if (selectionMode) {
                            // A selected tab follows the next press-and-move without requiring
                            // another long press before it can be dropped onto a group.
                            detectDragGestures(
                                onDragStart = {
                                    onDragStarted(tab)
                                    reportDragPosition(it)
                                },
                                onDrag = { change, _ -> reportDragPosition(change.position) },
                                onDragEnd = onDragFinished,
                                onDragCancel = onDragCancelled
                            )
                        } else {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    onDragStarted(tab)
                                    reportDragPosition(it)
                                },
                                onDrag = { change, _ -> reportDragPosition(change.position) },
                                onDragEnd = onDragFinished,
                                onDragCancel = onDragCancelled
                            )
                        }
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(90.dp)
                .height(70.dp)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (isPrivateBrowsingHome) {
                Image(
                    painter = painterResource(R.drawable.icons_privacy_mask),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            } else {
                TabThumbnail(tab.id, 90.dp, thumbnailStorage, contentBlockerState)
            }
        }

        Column(modifier = Modifier
            .weight(2f)
            .padding(start = 12.dp)) {
            Text(
                text = if (isTabBlocked) stringResource(id = R.string.blocked_website)
                        else if (isPrivateBrowsingHome) stringResource(id = R.string.browser_new_tab_private)
                        else tab.content.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            Text(
                tab.content.url.toCleanHost(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (selectionMode) {
            Checkbox(
                checked = isSelectedForGrouping,
                onCheckedChange = { onSelected(tab) }
            )
        } else if (groupId != null) {
            IconButton(onClick = { onRemoveFromGroup(groupId, tab.id) }) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_folder),
                    contentDescription = stringResource(R.string.browser_remove_tab_from_group)
                )
            }
        } else {
            IconButton(onClick = { onDeleted(tab) }) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_close),
                    contentDescription = stringResource(R.string.tab_tray_close_tab),
                )
            }
        }
    }
}
