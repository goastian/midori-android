package org.midorinext.android.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.newtab.isNativeHomeUrl
import org.midorinext.android.ui.animation.reduceMotionRequested
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The item order is shared with [SmartTabsState.selectedItemIndex]: groups, active, inactive. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCarousel(
    tabs: List<TabSessionState>,
    state: LazyListState,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (TabSessionState) -> Unit,
    onTabDeleted: (TabSessionState) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabLongPressed: (TabSessionState) -> Unit = {},
    groups: List<SmartTabGroup> = emptyList(),
    inactiveTabs: List<TabSessionState> = emptyList(),
    onOpenGroup: (SmartTabGroup) -> Unit = {},
    onEditGroup: (SmartTabGroup) -> Unit = {},
    onAddTabsToGroup: (SmartTabGroup) -> Unit = {},
    onDeleteGroup: (SmartTabGroup) -> Unit = {},
    dragEnabled: Boolean = false,
    draggingTabId: String? = null,
    dropTargetId: String? = null,
    onGroupBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onTabDragStarted: (TabSessionState) -> Unit = {},
    onTabDragPositionChanged: (Offset) -> Unit = {},
    onTabDragFinished: () -> Unit = {},
    onTabDragCancelled: () -> Unit = {}
) {
    val count = groups.size + tabs.size + inactiveTabs.size
    if (count == 0) return
    val reduceMotion = reduceMotionRequested()
    val scope = rememberCoroutineScope()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    val navigateTo: (Int) -> Unit = { index ->
        navigationJob?.cancel()
        navigationJob = scope.launch {
            if (reduceMotion) state.scrollToItem(index) else state.animateScrollToItem(index)
        }
    }

    val centeredIndex by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index ?: state.firstVisibleItemIndex
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Keep the next page visible on phones and avoid oversized screenshots on tablets.
            val cardWidth = (maxWidth * 0.70f).coerceAtMost(360.dp)
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            val previewHeight = (cardWidth / 0.62f)
                .coerceAtMost((maxHeight - 80.dp).coerceAtLeast(0.dp))
            val pageModifier = Modifier.width(cardWidth)
            fun pageMotion(key: String) = pageModifier.graphicsLayer {
                val layout = state.layoutInfo
                val item = layout.visibleItemsInfo.firstOrNull { it.key == key }
                val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                val distance = if (reduceMotion || item == null) 0f
                    else (abs(item.offset + item.size / 2f - center) / item.size).coerceIn(0f, 1f)
                scaleX = 1f - distance * 0.08f
                scaleY = scaleX
                alpha = 1f - distance * 0.18f
            }

            LazyRow(
                state = state,
                modifier = Modifier.fillMaxSize().testTag("tabs_carousel"),
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                userScrollEnabled = draggingTabId == null,
                flingBehavior = rememberSnapFlingBehavior(
                    lazyListState = state,
                    snapPosition = SnapPosition.Center
                )
            ) {
                items(groups, key = { "carousel-group-${it.id}" }, contentType = { "group" }) { group ->
                    TabGroupCard(
                        group = group,
                        selected = group.tabs.any { it.id == selectedTabId },
                        thumbnailStorage = thumbnailStorage,
                        onOpen = { onOpenGroup(group) },
                        onEdit = { onEditGroup(group) },
                        onAddTabs = { onAddTabsToGroup(group) },
                        onDelete = { onDeleteGroup(group) },
                        isDropTarget = dropTargetId == group.id,
                        onBoundsChanged = { bounds -> onGroupBoundsChanged(group.id, bounds) },
                        modifier = (if (reduceMotion) Modifier else Modifier.animateItem())
                            .then(pageMotion("carousel-group-${group.id}")),
                        previewHeight = previewHeight
                    )
                }
                // Separate intervals preserve stable keys without copying the entire tab list.
                for ((prefix, section) in listOf("active" to tabs, "inactive" to inactiveTabs)) {
                    items(section, key = { "carousel-$prefix-${it.id}" }, contentType = { "tab" }) { tab ->
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
                            dragEnabled = dragEnabled,
                            isBeingDragged = draggingTabId == tab.id,
                            onDragStarted = onTabDragStarted,
                            onDragPositionChanged = onTabDragPositionChanged,
                            onDragFinished = onTabDragFinished,
                            onDragCancelled = onTabDragCancelled,
                            previewHeight = previewHeight,
                            dismissVertically = true,
                            modifier = (if (reduceMotion) Modifier else Modifier.animateItem())
                                .then(pageMotion("carousel-$prefix-${tab.id}"))
                        )
                    }
                }
            }
        }
        CarouselPreviews(
            groups = groups,
            tabs = tabs,
            inactiveTabs = inactiveTabs,
            centeredIndex = centeredIndex.coerceIn(0, count - 1),
            thumbnailStorage = thumbnailStorage,
            onNavigate = navigateTo,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.browser_tab_carousel_position,
                    (centeredIndex + 1).coerceIn(1, count),
                    count
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (centeredIndex >= groups.size + tabs.size && inactiveTabs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.browser_inactive_tabs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CarouselPreviews(
    groups: List<SmartTabGroup>,
    tabs: List<TabSessionState>,
    inactiveTabs: List<TabSessionState>,
    centeredIndex: Int,
    thumbnailStorage: ThumbnailStorage,
    onNavigate: (Int) -> Unit,
) {
    val stripState = rememberLazyListState(initialFirstVisibleItemIndex = centeredIndex)
    val reduceMotion = reduceMotionRequested()
    LaunchedEffect(centeredIndex) {
        if (reduceMotion) stripState.scrollToItem(centeredIndex)
        else stripState.animateScrollToItem(centeredIndex)
    }
    val shape = RoundedCornerShape(6.dp)
    val colors = MaterialTheme.colorScheme
    fun previewModifier(index: Int, description: String, tag: String) = Modifier
        .size(48.dp, 64.dp)
        .testTag(tag)
        .clip(RoundedCornerShape(8.dp))
        .semantics { contentDescription = description }
        .selectable(selected = index == centeredIndex, role = Role.Tab, onClick = { onNavigate(index) })
        .padding(horizontal = 6.dp, vertical = 8.dp)
        .border(if (index == centeredIndex) 2.dp else 1.dp,
            if (index == centeredIndex) colors.primary else colors.outlineVariant, shape)
        .padding(2.dp)
        .clip(shape)
        .background(colors.surfaceVariant)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        LazyRow(
            state = stripState,
            modifier = Modifier.fillMaxWidth().testTag("tabs_carousel_previews"),
            contentPadding = PaddingValues(horizontal = ((maxWidth - 48.dp) / 2).coerceAtLeast(0.dp)),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(groups, key = { _, group -> "preview-group-${group.id}" }, contentType = { _, _ -> "group" }) { index, group ->
                TabGroupPreview(group, thumbnailStorage,
                    previewModifier(index, group.name, "tabs_carousel_preview_group:${group.id}"))
            }
            for ((prefix, section) in listOf("active" to tabs, "inactive" to inactiveTabs)) {
                val offset = groups.size + if (prefix == "inactive") tabs.size else 0
                itemsIndexed(section, key = { _, tab -> "preview-$prefix-${tab.id}" }, contentType = { _, _ -> "tab" }) { index, tab ->
                    val title = when {
                        tab.content.private && (tab.content.url.isBlank() || isNativeHomeUrl(tab.content.url)) -> stringResource(R.string.browser_new_tab_private)
                        isNativeHomeUrl(tab.content.url) -> stringResource(R.string.browser_new_tab)
                        else -> tab.content.title.ifBlank { tab.content.url }
                    }
                    Box(previewModifier(offset + index, title, "tabs_carousel_preview:${tab.id}")) {
                        TabPreview(tab, thumbnailStorage, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
