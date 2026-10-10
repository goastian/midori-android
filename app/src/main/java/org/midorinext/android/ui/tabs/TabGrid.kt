package org.midorinext.android.ui.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.browser.home.HomePrivateBrowsingContent
import org.midorinext.android.ui.widgets.MidoriIconOnBackground
import org.midorinext.android.ui.animation.reduceMotionRequested
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabGrid(
    tabs: List<TabSessionState>,
    state: LazyGridState,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (tab: TabSessionState) -> Unit,
    onTabDeleted: (tab: TabSessionState) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabSelectionChange: (String) -> Unit = {},
    onTabLongPressed: (TabSessionState) -> Unit = {}
) {
    val animationsEnabled = !reduceMotionRequested()
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(
            items = tabs,
            key = { it.id },
            contentType = { "tab" },
        ) { tab ->
            TabCard(
                tab = tab,
                selected = tab.id == selectedTabId,
                thumbnailStorage = thumbnailStorage,
                browserIcons = browserIcons,
                onSelected = onTabSelected,
                onDeleted = onTabDeleted,
                selectionMode = selectionMode,
                isSelectedForGrouping = tab.id in selectedTabIds,
                onLongPressed = onTabLongPressed,
                modifier = if (animationsEnabled) Modifier.animateItem() else Modifier
            )
        }
    }
}

@Composable
fun TabCard(
    tab: TabSessionState,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onSelected: (tab: TabSessionState) -> Unit,
    onDeleted: (tab: TabSessionState) -> Unit,
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
    var deleting by remember { mutableStateOf(false) }
    val animationsEnabled = !reduceMotionRequested()
    val scale by animateFloatAsState(
        targetValue = if (deleting) 0f else 1f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 140) else snap(),
        finishedListener = {
            if (it == 0f) {
                onDeleted(tab)
                deleting = false
            }
        },
        label = "tabSwipeScale",
    )
    val isPrivateBrowsingHome = tab.content.private && tab.content.url.isBlank()
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Column(modifier = modifier
        .scale(scale)
        .alpha(if (isBeingDragged) 0.42f else 1f)
        .onGloballyPositioned { coordinates = it }
        .clip(MaterialTheme.shapes.extraSmall)
        .then(
            if (dragEnabled) {
                // Drag and selection must share the same long-press stream. A combinedClickable
                // long-click consumes it before the drag detector can receive movement.
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
                        coordinates?.boundsInWindow()?.let { bounds ->
                            onDragPositionChanged(
                                Offset(bounds.left + position.x, bounds.top + position.y)
                            )
                        }
                    }
                    if (selectionMode) {
                        // After selection, Firefox starts the drag with the next press-and-move.
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
        .background(
            if (selected || isSelectedForGrouping) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides
            if (selected || isSelectedForGrouping) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSecondaryContainer)
        {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier
                    .padding(start = 10.dp)
                    .size(18.dp)
                ) {
                    if (isPrivateBrowsingHome) {
                        MidoriIconOnBackground(shape = RoundedCornerShape(4.dp))
                    } else {
                        tab.content.icon?.let {
                            Image(bitmap = it.asImageBitmap(), contentDescription = null)
                        } ?: browserIcons.Loader(url = tab.content.url, isPrivate = tab.content.private) {
                            WithIcon {
                                Image(painter = it.painter, contentDescription = null)
                            }
                            Placeholder {
                                Image(painter = painterResource(id = R.drawable.icons_internet), contentDescription = null)
                            }
                        }
                    }
                }

                Text(
                    text = if (isPrivateBrowsingHome) stringResource(id = R.string.browser_new_tab_private)
                            else tab.content.title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(2f)
                )
                if (selectionMode) {
                    Checkbox(
                        checked = isSelectedForGrouping,
                        onCheckedChange = { onSelected(tab) }
                    )
                } else if (groupId != null) {
                    ToolbarAction(onClick = { onRemoveFromGroup(groupId, tab.id) }) {
                        Icon(
                            painterResource(id = R.drawable.icons_folder),
                            contentDescription = stringResource(R.string.browser_remove_tab_from_group)
                        )
                    }
                } else {
                    ToolbarAction(
                        onClick = { deleting = true },
                    ) { // TODO rename ToolbarAction to "SmallButton" and put it in widgets
                        Icon(
                            painterResource(id = R.drawable.icons_close),
                            contentDescription = stringResource(R.string.tab_tray_close_tab),
                        )
                    }
                }
            }
            Box(modifier = Modifier
                .defaultMinSize(150.dp, 175.dp)
                .padding(top = 0.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                .height(175.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                ),
                propagateMinConstraints = true
            ) {
                // TODO size equal to calculated size ?
                if (isPrivateBrowsingHome) {
                    HomePrivateBrowsingContent(
                        iconColor = LocalContentColor.current,
                        iconScale = 0.5f,
                        iconPaddingBottom = 8.dp,
                        titleFontSize = 16.sp,
                        titleLineHeight = 16.sp,
                        textFontSize = 10.sp,
                        textLineHeight = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    TabThumbnail(
                        tabId = tab.id,
                        private = tab.content.private,
                        thumbnailStorage = thumbnailStorage,
                    )
                }
            }
        }
    }
}
