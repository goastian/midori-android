package org.midorinext.android.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem

@Composable
fun SmartTabView(
    state: SmartTabsState,
    private: Boolean,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (tab: TabSessionState) -> Unit,
    onTabDeleted: (tab: TabSessionState) -> Unit,
    contentBlockerState: ContentBlockerState,
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
    onRemoveTabFromGroup: (String, String) -> Unit = { _, _ -> }
) {
    val activeTabs = remember(state.activeTabs, private) {
        state.activeTabs.filter { it.content.private == private }
    }
    val inactiveTabs = remember(state.inactiveTabs, private) {
        state.inactiveTabs.filter { it.content.private == private }
    }
    val groups = remember(state.groups, private) {
        if (private) emptyList() else state.groups
    }

    if (selectionTargetGroupId != null) {
        return TabView(
            tabs = (activeTabs + inactiveTabs),
            private = private,
            selectedTabId = selectedTabId,
            thumbnailStorage = thumbnailStorage,
            browserIcons = browserIcons,
            onTabSelected = onTabSelected,
            onTabDeleted = onTabDeleted,
            contentBlockerState = contentBlockerState,
            modifier = modifier,
            tabsViewOption = tabsViewOption,
            selectionMode = selectionMode,
            selectedTabIds = selectedTabIds,
            onTabSelectionChange = onTabSelectionChange,
            onTabLongPressed = onTabLongPressed
        )
    }

    if (tabsViewOption == TabsViewOption.GRID) {
        return SmartTabsGrid(
            groups = groups,
            activeTabs = activeTabs,
            inactiveTabs = inactiveTabs,
            private = private,
            selectedTabId = selectedTabId,
            thumbnailStorage = thumbnailStorage,
            browserIcons = browserIcons,
            onTabSelected = onTabSelected,
            onTabDeleted = onTabDeleted,
            contentBlockerState = contentBlockerState,
            modifier = modifier,
            selectionMode = selectionMode,
            selectedTabIds = selectedTabIds,
            onTabLongPressed = onTabLongPressed,
            onEditGroup = onEditGroup,
            onAddTabsToGroup = onAddTabsToGroup,
            onDeleteGroup = onDeleteGroup,
            onOpenGroup = onOpenGroup,
            onRemoveTabFromGroup = onRemoveTabFromGroup
        )
    }

    val hasSections = groups.isNotEmpty() || inactiveTabs.isNotEmpty()
    if (!hasSections) {
        return TabView(
            tabs = activeTabs,
            private = private,
            selectedTabId = selectedTabId,
            thumbnailStorage = thumbnailStorage,
            browserIcons = browserIcons,
            onTabSelected = onTabSelected,
            onTabDeleted = onTabDeleted,
            contentBlockerState = contentBlockerState,
            modifier = modifier,
            tabsViewOption = tabsViewOption,
            selectionMode = selectionMode,
            selectedTabIds = selectedTabIds,
            onTabSelectionChange = onTabSelectionChange,
            onTabLongPressed = onTabLongPressed
        )
    }

    if (activeTabs.isEmpty() && inactiveTabs.isEmpty() && groups.isEmpty()) {
        EmptyTabsPlaceholder(private)
        return
    }

    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            item(key = "group-${group.id}") {
                TabGroupCard(
                    group = group,
                    selected = group.tabs.any { it.id == selectedTabId },
                    thumbnailStorage = thumbnailStorage,
                    contentBlockerState = contentBlockerState,
                    onOpen = { onOpenGroup(group) },
                    onEdit = { onEditGroup(group) },
                    onAddTabs = { onAddTabsToGroup(group) },
                    onDelete = { onDeleteGroup(group) }
                )
            }
        }

        if (activeTabs.isNotEmpty()) {
            item(key = "active-header") {
                TabSectionHeader(
                    title = stringResource(R.string.browser_active_tabs),
                    subtitle = stringResource(R.string.browser_tab_group_count, activeTabs.size)
                )
            }
            items(activeTabs, key = { "active-${it.id}" }) { tab ->
                TabRow(
                    tab = tab,
                    selected = tab.id == selectedTabId,
                    thumbnailStorage = thumbnailStorage,
                    onSelected = onTabSelected,
                    onDeleted = onTabDeleted,
                    contentBlockerState = contentBlockerState,
                    selectionMode = selectionMode,
                    isSelectedForGrouping = tab.id in selectedTabIds,
                    onLongPressed = onTabLongPressed
                )
            }
        }

        if (inactiveTabs.isNotEmpty()) {
            item(key = "inactive-header") {
                TabSectionHeader(
                    title = stringResource(R.string.browser_inactive_tabs),
                    subtitle = stringResource(R.string.browser_inactive_tabs_summary)
                )
            }
            items(inactiveTabs, key = { "inactive-${it.id}" }) { tab ->
                TabRow(
                    tab = tab,
                    selected = tab.id == selectedTabId,
                    thumbnailStorage = thumbnailStorage,
                    onSelected = onTabSelected,
                    onDeleted = onTabDeleted,
                    contentBlockerState = contentBlockerState,
                    selectionMode = selectionMode,
                    isSelectedForGrouping = tab.id in selectedTabIds,
                    onLongPressed = onTabLongPressed
                )
            }
        }
    }
}

@Composable
private fun SmartTabsGrid(
    groups: List<SmartTabGroup>,
    activeTabs: List<TabSessionState>,
    inactiveTabs: List<TabSessionState>,
    private: Boolean,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (TabSessionState) -> Unit,
    onTabDeleted: (TabSessionState) -> Unit,
    contentBlockerState: ContentBlockerState,
    modifier: Modifier,
    selectionMode: Boolean,
    selectedTabIds: Set<String>,
    onTabLongPressed: (TabSessionState) -> Unit,
    onEditGroup: (SmartTabGroup) -> Unit,
    onAddTabsToGroup: (SmartTabGroup) -> Unit,
    onDeleteGroup: (SmartTabGroup) -> Unit,
    onOpenGroup: (SmartTabGroup) -> Unit,
    onRemoveTabFromGroup: (String, String) -> Unit,
) {
    if (groups.isEmpty() && activeTabs.isEmpty() && inactiveTabs.isEmpty()) {
        EmptyTabsPlaceholder(private)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        groups.forEach { group ->
            item(key = "grid-group-${group.id}") {
                TabGroupCard(
                    group = group,
                    selected = group.tabs.any { it.id == selectedTabId },
                    thumbnailStorage = thumbnailStorage,
                    contentBlockerState = contentBlockerState,
                    onOpen = { onOpenGroup(group) },
                    onEdit = { onEditGroup(group) },
                    onAddTabs = { onAddTabsToGroup(group) },
                    onDelete = { onDeleteGroup(group) }
                )
            }
        }

        if (activeTabs.isNotEmpty()) {
            if (groups.isEmpty()) {
                item(key = "grid-active-header", span = { GridItemSpan(maxLineSpan) }) {
                    TabSectionHeader(
                        title = stringResource(R.string.browser_active_tabs),
                        subtitle = stringResource(R.string.browser_tab_group_count, activeTabs.size)
                    )
                }
            }
            gridItems(activeTabs, key = { "grid-active-${it.id}" }) { tab ->
                TabCard(
                    tab = tab,
                    selected = tab.id == selectedTabId,
                    thumbnailStorage = thumbnailStorage,
                    browserIcons = browserIcons,
                    onSelected = onTabSelected,
                    onDeleted = onTabDeleted,
                    contentBlockerState = contentBlockerState,
                    selectionMode = selectionMode,
                    isSelectedForGrouping = tab.id in selectedTabIds,
                    onLongPressed = onTabLongPressed
                )
            }
        }

        if (inactiveTabs.isNotEmpty()) {
            item(key = "grid-inactive-header", span = { GridItemSpan(maxLineSpan) }) {
                TabSectionHeader(
                    title = stringResource(R.string.browser_inactive_tabs),
                    subtitle = stringResource(R.string.browser_inactive_tabs_summary)
                )
            }
            gridItems(inactiveTabs, key = { "grid-inactive-${it.id}" }) { tab ->
                TabCard(
                    tab = tab,
                    selected = tab.id == selectedTabId,
                    thumbnailStorage = thumbnailStorage,
                    browserIcons = browserIcons,
                    onSelected = onTabSelected,
                    onDeleted = onTabDeleted,
                    contentBlockerState = contentBlockerState,
                    selectionMode = selectionMode,
                    isSelectedForGrouping = tab.id in selectedTabIds,
                    onLongPressed = onTabLongPressed
                )
            }
        }
    }
}

@Composable
private fun TabGroupCard(
    group: SmartTabGroup,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    contentBlockerState: ContentBlockerState,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onAddTabs: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val color = group.color.toComposeColor()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(if (selected) 4.dp else 3.dp, color, shape)
            .clip(shape)
            .clickable(onClick = onOpen),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.browser_tab_group_count, group.tabs.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
                IconButton(onClick = onAddTabs, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.icons_folder_add),
                        contentDescription = stringResource(R.string.browser_add_tabs_to_group),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.icons_more_vertical),
                            contentDescription = stringResource(R.string.browser_manage_tab_group),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Dropdown(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownItem(
                            text = stringResource(R.string.browser_edit_tab_group),
                            icon = R.drawable.icons_edit,
                            onClick = {
                                showMenu = false
                                onEdit()
                            }
                        )
                        DropdownItem(
                            text = stringResource(R.string.browser_delete_tab_group),
                            icon = R.drawable.icons_close,
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                group.tabs.take(2).forEach { tab ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        TabThumbnail(
                            tabId = tab.id,
                            size = 160.dp,
                            thumbnailStorage = thumbnailStorage,
                            contentBlockerState = contentBlockerState
                        )
                    }
                }
                repeat((2 - group.tabs.size.coerceAtMost(2)).coerceAtLeast(0)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TabGroupTabsSheet(
    group: SmartTabGroup,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    contentBlockerState: ContentBlockerState,
    onDismissRequest: () -> Unit,
    onTabSelected: (TabSessionState) -> Unit,
    onTabDeleted: (TabSessionState) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(group.color.toComposeColor(), CircleShape)
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.browser_tab_group_count, group.tabs.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(vertical = 12.dp)
            ) {
                gridItems(group.tabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        selected = tab.id == selectedTabId,
                        thumbnailStorage = thumbnailStorage,
                        browserIcons = browserIcons,
                        onSelected = onTabSelected,
                        onDeleted = onTabDeleted,
                        contentBlockerState = contentBlockerState
                    )
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
    contentBlockerState: ContentBlockerState,
    modifier: Modifier = Modifier,
    tabsViewOption: TabsViewOption = TabsViewOption.LIST,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabSelectionChange: (String) -> Unit = {},
    onTabLongPressed: (TabSessionState) -> Unit = {}
) {
    if (tabs.isNotEmpty()) {
        when (tabsViewOption) {
            TabsViewOption.LIST -> TabList(
                tabs = tabs,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = contentBlockerState,
                modifier = modifier,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                onTabLongPressed = onTabLongPressed
            )
            TabsViewOption.GRID -> TabGrid(
                tabs = tabs,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                browserIcons = browserIcons,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = contentBlockerState,
                modifier = modifier,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
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
    val privateMode = remember { private }
    EmptyPagePlaceholder(
        icon = if (privateMode) R.drawable.icons_privacy_mask else R.drawable.icons_tab_smiley,
        title = stringResource(id = if (privateMode) R.string.browser_tabs_empty_title_private else R.string.browser_tabs_empty_title),
        subtitle = stringResource(id = if (privateMode) R.string.browser_tabs_empty_subtitle_private else R.string.browser_tabs_empty_subtitle)
    )
}
