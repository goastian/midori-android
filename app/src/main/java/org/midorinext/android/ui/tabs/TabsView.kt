package org.midorinext.android.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.ui.animation.reduceMotionRequested
import kotlinx.coroutines.flow.collectLatest

@Composable
fun SmartTabView(
    state: SmartTabsState,
    private: Boolean,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (TabSessionState) -> Unit,
    onTabDeleted: (TabSessionState) -> Unit,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    tabsViewOption: TabsViewOption = TabsViewOption.LIST,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabSelectionChange: (String) -> Unit = {},
    onTabLongPressed: (TabSessionState) -> Unit = {},
    selectionTargetGroupId: String? = null,
    onEditGroup: (SmartTabGroup) -> Unit = {},
    onAddTabsToGroup: (SmartTabGroup) -> Unit = {},
    onDeleteGroup: (SmartTabGroup) -> Unit = {},
    onOpenGroup: (SmartTabGroup) -> Unit = {},
    onUngroup: (SmartTabGroup) -> Unit = {},
    onSelectGroup: (SmartTabGroup) -> Unit = {},
    onNewTabInGroup: (SmartTabGroup) -> Unit = {},
    expandedGroupId: String? = null,
    onCollapseGroup: () -> Unit = {},
    onTabsDropped: (Set<String>, TabDropTarget) -> Unit = { _, _ -> },
    onDragMoving: () -> Unit = {},
    onStartSelection: () -> Unit = {},
    onRemoveTabFromGroup: (String, String) -> Unit = { _, _ -> },
    carouselState: LazyListState = rememberLazyListState(),
) {
    val activeTabs = state.activeTabs
    val inactiveTabs = state.inactiveTabs
    val groups = state.groups
    val animationsEnabled = !reduceMotionRequested()
    val drag = remember { TabDragState() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val dragEnabled = !private && selectionTargetGroupId == null
    val groupMembers = remember(groups) { groups.associate { it.id to it.tabIds } }
    val tabsById = remember(state) { (activeTabs + inactiveTabs + groups.flatMap { it.tabs }).associateBy { it.id } }
    fun target() = drag.position?.let { tabDropTarget(it, drag.movingIds, drag.tabBounds, drag.groupBounds, groupMembers) }
    val dropTarget by remember(drag, groupMembers) {
        derivedStateOf { if (drag.moving) target() else null }
    }
    val begin: (TabSessionState) -> Unit = { tab ->
        drag.begin(tab.id, if (tab.id in selectedTabIds) selectedTabIds else setOf(tab.id), drag.tabBounds[tab.id])
        if (!selectionMode) onTabLongPressed(tab)
        else if (tab.id !in selectedTabIds) onTabSelectionChange(tab.id)
    }
    val move: (Offset) -> Unit = { pointer -> if (drag.move(pointer)) onDragMoving() }
    val finish: () -> Unit = {
        if (drag.moving) target()?.let { onTabsDropped(drag.movingIds, it) }
        drag.clear()
    }
    DisposableEffect(drag) { onDispose { drag.clear() } }
    LaunchedEffect(expandedGroupId, groups.size, tabsViewOption) {
        val index = groups.indexOfFirst { it.id == expandedGroupId }
        if (index >= 0) when (tabsViewOption) {
            TabsViewOption.CAROUSEL -> Unit
            TabsViewOption.LIST -> listState.animateScrollToItem(index)
            else -> gridState.animateScrollToItem(index)
        }
    }
    LaunchedEffect(drag, tabsViewOption, density) {
        snapshotFlow {
            val pointer = drag.position
            if (!drag.moving || pointer == null) 0f else {
                val horizontal = tabsViewOption == TabsViewOption.CAROUSEL
                val position = if (horizontal) pointer.x else pointer.y
                val start = if (horizontal) viewport.left else viewport.top
                val end = if (horizontal) viewport.right else viewport.bottom
                val edge = with(density) { 48.dp.toPx() }
                when {
                    position < start + edge -> -((start + edge - position) / edge).coerceIn(0f, 1f)
                    position > end - edge -> ((position - end + edge) / edge).coerceIn(0f, 1f)
                    else -> 0f
                }
            }
        }.collectLatest { fraction ->
            if (fraction == 0f) return@collectLatest
            var previousFrame = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val elapsed = ((now - previousFrame) / 1_000_000f).coerceAtMost(32f)
                previousFrame = now
                val amount = with(density) { 600.dp.toPx() } * elapsed / 1000f * fraction
                when (tabsViewOption) {
                    TabsViewOption.CAROUSEL -> carouselState.scrollBy(amount)
                    TabsViewOption.LIST -> listState.scrollBy(amount)
                    else -> gridState.scrollBy(amount)
                }
            }
        }
    }

    val tabContent: @Composable (TabSessionState, Modifier, Dp?) -> Unit = { tab, itemModifier, height ->
        val boundsModifier = itemModifier.tabDropBounds(drag, tab.id)
        val selectWithoutDrag: (TabSessionState) -> Unit = { selected ->
            if (drag.tabId == null) onTabSelected(selected)
        }
        if (tabsViewOption == TabsViewOption.LIST) {
            TabRow(tab, tab.id == selectedTabId, thumbnailStorage, selectWithoutDrag, onTabDeleted,
                selectionMode = selectionMode, isSelectedForGrouping = tab.id in selectedTabIds,
                onLongPressed = onTabLongPressed, dragEnabled = dragEnabled, dragGestureAtParent = true,
                isBeingDragged = drag.moving && drag.tabId == tab.id,
                onDragStarted = begin, onDragPositionChanged = move, onDragFinished = finish,
                onDragCancelled = drag::clear, modifier = boundsModifier)
        } else {
            TabCard(tab, tab.id == selectedTabId, thumbnailStorage, browserIcons, selectWithoutDrag, onTabDeleted,
                selectionMode = selectionMode, isSelectedForGrouping = tab.id in selectedTabIds,
                onLongPressed = onTabLongPressed, dragEnabled = dragEnabled, dragGestureAtParent = true,
                isBeingDragged = drag.moving && drag.tabId == tab.id,
                isDropTarget = dropTarget == TabDropTarget.Tab(tab.id),
                onDragStarted = begin, onDragPositionChanged = move, onDragFinished = finish,
                onDragCancelled = drag::clear, previewHeight = height,
                dismissVertically = tabsViewOption == TabsViewOption.CAROUSEL, modifier = boundsModifier)
        }
    }
    val groupContent: @Composable (SmartTabGroup, Modifier, Dp?) -> Unit = { group, itemModifier, height ->
        val isSelected = if (selectionMode) group.tabIds.all { it in selectedTabIds }
            else selectedTabId in group.tabIds
        val groupModifier = itemModifier.tabDropBounds(drag, group.id, group = true)
        if (group.id == expandedGroupId) {
            ExpandedTabGroup(group, thumbnailStorage, groupModifier, tabsViewOption == TabsViewOption.CAROUSEL,
                height, selectionMode, drag.tabId != null, onCollapseGroup, { onNewTabInGroup(group) },
                { onUngroup(group) }, { onEditGroup(group) }, onStartSelection, { onDeleteGroup(group) }, tabContent)
        } else {
            TabGroupCard(group, isSelected, thumbnailStorage,
                onOpen = { if (selectionMode) onSelectGroup(group) else onOpenGroup(group) },
                onEdit = { onEditGroup(group) }, onAddTabs = { onAddTabsToGroup(group) },
                onDelete = { onDeleteGroup(group) }, isDropTarget = dropTarget == TabDropTarget.Group(group.id),
                onBoundsChanged = {}, modifier = groupModifier,
                previewHeight = height, compact = tabsViewOption == TabsViewOption.LIST,
                selectionMode = selectionMode, onSelect = { onSelectGroup(group) },
                onUngroup = { onUngroup(group) })
        }
    }
    var awaitingDragSource by remember { mutableStateOf(false) }
    val gesture = if (dragEnabled) Modifier.tabDragGestures(
        id = "tab-tray",
        onStart = { awaitingDragSource = true },
        onPosition = { position ->
            if (awaitingDragSource) {
                awaitingDragSource = false
                val id = drag.tabBounds.entries.firstOrNull { it.value.contains(position) }?.key
                tabsById[id]?.let(begin)
            }
            if (drag.tabId != null) move(position)
        },
        onEnd = { awaitingDragSource = false; finish() },
        onCancel = { awaitingDragSource = false; drag.clear() },
    ) else Modifier
    Box(modifier.onGloballyPositioned { viewport = it.boundsInWindow() }.then(gesture)) {
        when {
            state.isEmpty -> EmptyTabsPlaceholder(private)
            selectionTargetGroupId != null -> TabView(
                tabs = activeTabs + inactiveTabs, private = private, selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage, browserIcons = browserIcons,
                onTabSelected = onTabSelected, onTabDeleted = onTabDeleted,
                listState = listState, gridState = gridState, carouselState = carouselState,
                modifier = Modifier.fillMaxSize(), tabsViewOption = tabsViewOption,
                selectionMode = selectionMode, selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange, onTabLongPressed = onTabLongPressed)
            tabsViewOption == TabsViewOption.CAROUSEL -> TabCarousel(
                tabs = activeTabs, inactiveTabs = inactiveTabs, groups = groups, state = carouselState,
                selectedTabId = selectedTabId, thumbnailStorage = thumbnailStorage, browserIcons = browserIcons,
                onTabSelected = onTabSelected, onTabDeleted = onTabDeleted, modifier = Modifier.fillMaxSize(),
                draggingTabId = drag.tabId, expandedGroupId = expandedGroupId,
                tabContent = { tab, itemModifier, height -> tabContent(tab, itemModifier, height) },
                groupContent = { group, itemModifier, height -> groupContent(group, itemModifier, height) })
            tabsViewOption == TabsViewOption.GRID -> LazyVerticalGrid(
                state = gridState, columns = GridCells.Adaptive(150.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = drag.tabId == null,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier.fillMaxSize().testTag("tabs_grid"),
            ) {
                groups.forEach { group ->
                    item(key = "grid-group-${group.id}", contentType = "group",
                        span = { GridItemSpan(if (group.id == expandedGroupId) maxLineSpan else 1) }) {
                        groupContent(group, if (animationsEnabled) Modifier.animateItem() else Modifier, null)
                    }
                }
                for ((prefix, section) in listOf("active" to activeTabs, "inactive" to inactiveTabs)) {
                    if (section.isNotEmpty() && (prefix == "inactive" || groups.isEmpty())) {
                        item(key = "$prefix-header", contentType = "header", span = { GridItemSpan(maxLineSpan) }) {
                            TabSectionHeader(stringResource(if (prefix == "active") R.string.browser_active_tabs else R.string.browser_inactive_tabs),
                                stringResource(R.string.browser_tab_group_count, section.size))
                        }
                    }
                    gridItems(section, key = { "grid-$prefix-${it.id}" }, contentType = { "tab" }) { tab ->
                        tabContent(tab, if (animationsEnabled) Modifier.animateItem() else Modifier, null)
                    }
                }
            }
            else -> LazyColumn(state = listState, userScrollEnabled = drag.tabId == null,
                contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize().testTag("tabs_list")) {
                groups.forEach { group ->
                    item(key = "group-${group.id}", contentType = "group") {
                        groupContent(group, (if (animationsEnabled) Modifier.animateItem() else Modifier)
                            .padding(horizontal = 16.dp, vertical = 4.dp), null)
                    }
                }
                for ((prefix, section) in listOf("active" to activeTabs, "inactive" to inactiveTabs)) {
                    if (section.isNotEmpty() && (groups.isNotEmpty() || inactiveTabs.isNotEmpty())) {
                        item(key = "$prefix-header", contentType = "header") {
                            TabSectionHeader(stringResource(if (prefix == "active") R.string.browser_active_tabs else R.string.browser_inactive_tabs),
                                stringResource(R.string.browser_tab_group_count, section.size))
                        }
                    }
                    items(section, key = { "$prefix-${it.id}" }, contentType = { "tab" }) { tab ->
                        tabContent(tab, if (animationsEnabled) Modifier.animateItem() else Modifier, null)
                    }
                }
            }
        }
        if (drag.moving) tabsById[drag.tabId]?.let { tab ->
            DraggedTabPreview(drag, tab, thumbnailStorage, viewport)
        }
    }

}

@Composable
internal fun TabGroupCard(
    group: SmartTabGroup,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAddTabs: () -> Unit,
    onDelete: () -> Unit,
    isDropTarget: Boolean,
    onBoundsChanged: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    previewHeight: Dp? = null,
    compact: Boolean = false,
    selectionMode: Boolean = false,
    onSelect: () -> Unit = {},
    onUngroup: () -> Unit = {},
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    val color = group.color.toComposeColor()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tab_group:${group.id}")
            .onGloballyPositioned { onBoundsChanged(it.boundsInWindow()) }
            .semantics { this.selected = selected }
            .border(
                width = if (selected || isDropTarget) 2.dp else 1.dp,
                color = when {
                    isDropTarget -> color
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clip(shape)
            .combinedClickable(onClick = onOpen, onLongClick = onSelect),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDropTarget) color.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) 88.dp else 56.dp)
                .padding(start = if (compact) 10.dp else 12.dp)
        ) {
            if (compact) {
                TabGroupPreview(
                    group = group,
                    thumbnailStorage = thumbnailStorage,
                    modifier = Modifier.width(72.dp).height(64.dp).clip(RoundedCornerShape(10.dp))
                )
            }
            GroupCount(group)
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    text = group.name,
                    style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.browser_tab_group_count, group.tabs.size),
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectionMode) {
                RadioButton(selected = selected, onClick = onSelect)
            } else {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.icons_close), stringResource(R.string.browser_close_all_tabs))
                }
            }
        }
        if (!compact) {
            TabGroupPreview(
                group = group,
                thumbnailStorage = thumbnailStorage,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                    .then(previewHeight?.let { Modifier.height(it) } ?: Modifier.aspectRatio(0.76f))
            )
        }
    }
}

@Composable
internal fun TabGroupPreview(
    group: SmartTabGroup,
    thumbnailStorage: ThumbnailStorage,
    modifier: Modifier = Modifier
) {
    val rows = 2
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(2) { column ->
                    val tab = group.tabs.getOrNull(row * 2 + column)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (tab != null) {
                            TabPreview(tab, thumbnailStorage, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun TabGroupColor.toComposeColor(): Color = when (this) {
    TabGroupColor.BLUE -> Color(0xFF1976D2)
    TabGroupColor.TEAL -> Color(0xFF00897B)
    TabGroupColor.GREEN -> Color(0xFF43A047)
    TabGroupColor.ORANGE -> Color(0xFFFB8C00)
    TabGroupColor.RED -> Color(0xFFE53935)
    TabGroupColor.PURPLE -> Color(0xFF8E24AA)
}

@Composable
fun TabView(
    tabs: List<TabSessionState>,
    private: Boolean,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (tab: TabSessionState) -> Unit,
    onTabDeleted: (tab: TabSessionState) -> Unit,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    tabsViewOption: TabsViewOption = TabsViewOption.LIST,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabSelectionChange: (String) -> Unit = {},
    onTabLongPressed: (TabSessionState) -> Unit = {},
    carouselState: LazyListState = rememberLazyListState()
) {
    if (tabs.isNotEmpty()) {
        when (tabsViewOption) {
            TabsViewOption.LIST -> TabList(
                tabs = tabs,
                state = listState,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                modifier = modifier,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                onTabLongPressed = onTabLongPressed
            )
            TabsViewOption.GRID -> TabGrid(
                tabs = tabs,
                state = gridState,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                browserIcons = browserIcons,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                modifier = modifier,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                onTabLongPressed = onTabLongPressed
            )
            TabsViewOption.CAROUSEL -> TabCarousel(
                tabs = tabs,
                state = carouselState,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                browserIcons = browserIcons,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                modifier = modifier,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabLongPressed = onTabLongPressed
            )
            TabsViewOption.UNRECOGNIZED -> {}
        }
    } else {
        EmptyTabsPlaceholder(private)
    }
}

@Composable
private fun EmptyTabsPlaceholder(private: Boolean) {
    EmptyPagePlaceholder(
        icon = if (private) R.drawable.icons_privacy_mask else R.drawable.icons_tab_smiley,
        title = stringResource(id = if (private) R.string.browser_tabs_empty_title_private else R.string.browser_tabs_empty_title),
        subtitle = stringResource(id = if (private) R.string.browser_tabs_empty_subtitle_private else R.string.browser_tabs_empty_subtitle)
    )
}
