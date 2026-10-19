package org.midorinext.android.ui.tabs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.newtab.isNativeHomeUrl
import org.midorinext.android.ext.toCleanHost
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
            .testTag("tabs_grid")
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
    dragGestureAtParent: Boolean = false,
    isBeingDragged: Boolean = false,
    isDropTarget: Boolean = false,
    onDragStarted: (TabSessionState) -> Unit = {},
    onDragPositionChanged: (Offset) -> Unit = {},
    onDragFinished: () -> Unit = {},
    onDragCancelled: () -> Unit = {},
    previewHeight: Dp? = null,
    dismissVertically: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dismissal = rememberTabDismissal(tab.id) { onDeleted(tab) }
    val animationsEnabled = !reduceMotionRequested()
    val interactionSource = remember(tab.id) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = if (animationsEnabled) tween(100) else snap(),
        label = "tab press",
    )
    val isPrivateBrowsingHome = tab.content.private && (tab.content.url.isBlank() || isNativeHomeUrl(tab.content.url))
    val isNativeHome = isNativeHomeUrl(tab.content.url)
    val domain = remember(tab.content.url) { tab.content.url.toCleanHost() }
    val title = when {
        isPrivateBrowsingHome -> stringResource(R.string.browser_new_tab_private)
        isNativeHome -> stringResource(R.string.browser_new_tab)
        else -> tab.content.title.ifBlank { domain }
    }
    val highlighted = if (selectionMode) isSelectedForGrouping else selected
    val surfaceColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        tween(if (animationsEnabled) 140 else 0), label = "tab surface",
    )
    val borderColor by animateColorAsState(
        if (highlighted || isDropTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        tween(if (animationsEnabled) 140 else 0), label = "tab outline",
    )
    val shape = RoundedCornerShape(16.dp)

    Column(modifier = modifier
        .testTag("tab_card:${tab.id}")
        .fillMaxWidth()
        .graphicsLayer {
            val progress = dismissal.progress.value
            scaleX = pressScale * (if (isDropTarget) 0.94f else 1f) * (1f - 0.06f * progress)
            scaleY = scaleX
            val offset = dismissal.dragOffset
            if (dismissVertically) {
                translationY = offset + if (animationsEnabled) (-size.height - offset) * progress else 0f
            } else {
                translationX = offset + if (animationsEnabled) ((if (offset < 0) -size.width else size.width) - offset) * progress else 0f
            }
            alpha = (1f - progress) * if (isBeingDragged) 0f else 1f
        }
        .tabSwipeToDismiss(dismissal, enabled = !selectionMode && !isBeingDragged && !dismissal.requested, vertical = dismissVertically)
        .semantics { this.selected = highlighted }
        .border(
            width = if (highlighted || isDropTarget) 2.dp else 1.dp,
            color = borderColor,
            shape = shape
        )
        .clip(shape)
        .then(
            if (dragEnabled) {
                // Drag and selection must share the same long-press stream. A combinedClickable
                // long-click consumes it before the drag detector can receive movement.
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = !dismissal.requested,
                    onClick = { onSelected(tab) },
                )
            } else {
                Modifier.combinedClickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
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
        .background(surfaceColor)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface)
        {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) {
                Box(modifier = Modifier
                    .padding(start = 10.dp)
                    .size(18.dp)
                ) {
                    if (isPrivateBrowsingHome) {
                        Icon(painterResource(R.drawable.icons_privacy_mask), null, tint = MaterialTheme.colorScheme.primary)
                    } else if (isNativeHome) {
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

                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (domain.isNotBlank()) {
                        Text(
                            text = domain,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selectionMode) {
                    RadioButton(selected = isSelectedForGrouping, onClick = { onSelected(tab) })
                } else if (groupId != null) {
                    IconButton(
                        onClick = { onRemoveFromGroup(groupId, tab.id) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painterResource(id = R.drawable.icons_folder),
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
                            painterResource(id = R.drawable.icons_close),
                            contentDescription = stringResource(R.string.tab_tray_close_tab),
                        )
                    }
                }
            }
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp, start = 4.dp, end = 4.dp)
                .then(previewHeight?.let { Modifier.height(it) } ?: Modifier.aspectRatio(0.76f))
                .clip(
                    RoundedCornerShape(
                        topStart = 4.dp,
                        topEnd = 4.dp,
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp
                    )
                )
                .background(MaterialTheme.colorScheme.surface),
                propagateMinConstraints = true
            ) {
                TabPreview(tab, thumbnailStorage, Modifier.fillMaxSize())
            }
        }
    }
}
