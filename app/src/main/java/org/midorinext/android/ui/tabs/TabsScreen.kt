package org.midorinext.android.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.IOException
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import org.midorinext.android.ui.zap.ZapButton
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabsScreen(
    onClose: () -> Unit = {},
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    tabsViewModel: TabsScreenViewModel = hiltViewModel(),
    isCurrentDestination: Boolean = true,
    onPrivacyChanged: (Boolean) -> Unit = {},
) {
    val selectedTabIsPrivate by appViewModel.isPrivate.collectAsStateWithLifecycle()
    val tabs by tabsViewModel.tabs.collectAsStateWithLifecycle()
    val smartTabs by tabsViewModel.smartTabs.collectAsStateWithLifecycle()
    val tabsViewOption by tabsViewModel.tabsViewOption.collectAsStateWithLifecycle()
    val recentlyClosedCount by tabsViewModel.recentlyClosedCount.collectAsStateWithLifecycle()
    val restoreComplete by tabsViewModel.restoreComplete.collectAsStateWithLifecycle()
    val selectedTabId by tabsViewModel.selectedTabId.collectAsStateWithLifecycle()
    var uiState by rememberSaveable(stateSaver = TabsUiStateSaver) {
        mutableStateOf(
            TabsUiState(
                page = if (selectedTabIsPrivate) TabsPage.PRIVATE else TabsPage.NORMAL,
            ),
        )
    }
    var pageInitialized by rememberSaveable { mutableStateOf(false) }
    val private = uiState.page.isPrivate
    val selection = uiState.mode as? TabsMode.Selecting
    val selectionMode = selection != null
    val selectedTabIds = selection?.tabIds.orEmpty()
    val selectionTargetGroupId = selection?.targetGroupId
    val searchQuery = (uiState.mode as? TabsMode.Searching)?.query.orEmpty()
    val dispatch: (TabsUiAction) -> Unit = { action ->
        val nextState = uiState.reduce(action)
        if (nextState.page != uiState.page) onPrivacyChanged(nextState.page.isPrivate)
        uiState = nextState
    }
    SideEffect {
        if (isCurrentDestination) onPrivacyChanged(private)
    }
    var groupBeingEdited by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingDeleted by remember { mutableStateOf<SmartTabGroup?>(null) }
    var expandedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(smartTabs?.groups) {
        val id = expandedGroupId
        if (id != null && smartTabs?.groups?.none { it.id == id } == true) expandedGroupId = null
    }
    var groupingSnackbarJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val defaultGroupName = stringResource(R.string.tabs_default_group_name)
    val groupCreatedString = stringResource(R.string.tabs_group_created)
    val groupUngroupedString = stringResource(R.string.tabs_group_ungrouped)
    val groupFailedString = stringResource(R.string.tabs_group_failed)
    val undoString = stringResource(R.string.tabs_group_undo)
    val tabsAddedToGroupString = stringResource(R.string.browser_tabs_added_to_group)
    fun showGroupingUndo(change: TabGroupingChange, message: String) {
        groupingSnackbarJob?.cancel()
        groupingSnackbarJob = scope.launch {
            val result = appViewModel.snackbarHostState.showSnackbar(message, actionLabel = undoString,
                withDismissAction = false, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                tabsViewModel.undoGrouping(change)
                if (expandedGroupId == change.groupId) expandedGroupId = null
            }
        }
    }
    val dropTabs: (Set<String>, TabDropTarget) -> Unit = { tabIds, target ->
        scope.launch {
            try {
                tabsViewModel.dropTabs(tabIds, target, defaultGroupName)?.let { change ->
                    expandedGroupId = change.groupId
                    dispatch(TabsUiAction.ExitTransientMode)
                    val added = change.after.first { it.id == change.groupId }.tabIdsCount -
                        (change.before.firstOrNull { it.id == change.groupId }?.tabIdsCount ?: 0)
                    showGroupingUndo(change, if (target is TabDropTarget.Tab) groupCreatedString else tabsAddedToGroupString.format(added))
                }
            } catch (_: IOException) {
                appViewModel.showSnackbar(groupFailedString)
            }
        }
    }

    // A protobuf value saved by an incompatible app version can be UNRECOGNIZED.
    // Never pass that sentinel to Compose, which tries to obtain its numeric value.
    val resolvedTabsViewOption = when (tabsViewOption) {
        TabsViewOption.CAROUSEL -> TabsViewOption.CAROUSEL
        TabsViewOption.LIST -> TabsViewOption.LIST
        else -> TabsViewOption.GRID
    }

    val normalTabsCount = tabs.count { !it.content.private }
    val privateTabsCount = tabs.size - normalTabsCount
    val tabsClosedString = stringResource(R.string.browser_tabs_closed)

    LaunchedEffect(restoreComplete, selectedTabId, tabs) {
        if (restoreComplete && !pageInitialized) {
            val restoredSelectionIsPrivate = tabs
                .firstOrNull { tab -> tab.id == selectedTabId }
                ?.content
                ?.private
                ?: selectedTabIsPrivate
            dispatch(
                TabsUiAction.SelectPage(
                    if (restoredSelectionIsPrivate) TabsPage.PRIVATE else TabsPage.NORMAL,
                ),
            )
            pageInitialized = true
        }
    }

    val openIndividualTab: (Boolean) -> Unit = { privateTab ->
        // Creating a tab is always an escape hatch from the grouping flow. In particular, this
        // prevents a pending "add to group" action from swallowing the next new tab.
        dispatch(TabsUiAction.ExitTransientMode)
        tabsViewModel.openNewTab(privateTab)
        onClose()
    }

    BackHandler(
        enabled = (expandedGroupId != null || uiState.mode !is TabsMode.Browsing) &&
            groupBeingEdited == null &&
            groupBeingDeleted == null,
    ) {
        if (uiState.mode !is TabsMode.Browsing) dispatch(TabsUiAction.ExitTransientMode)
        else expandedGroupId = null
    }

    MidoriBrowserTheme(
        darkTheme = LocalMidoriTheme.current.dark,
        privacy = private,
        updateSystemBars = isCurrentDestination,
    ) {
        TabThumbnailCacheProvider(private) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("tabs_screen")
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (uiState.mode is TabsMode.Browsing) onClose()
                        else dispatch(TabsUiAction.ExitTransientMode)
                    }) {
                        Icon(painterResource(R.drawable.icons_arrow_backward), stringResource(R.string.nav_back))
                    }
                    Text(
                        text = when (val mode = uiState.mode) {
                            is TabsMode.Selecting -> stringResource(R.string.browser_group_tabs_selected, mode.tabIds.size)
                            is TabsMode.Searching -> stringResource(R.string.browser_search_tabs)
                            TabsMode.Browsing -> stringResource(R.string.tab_tray_normal_tabs)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    if (selectionMode) {
                        TabsSelectionMenu(
                            groups = if (private) emptyList() else smartTabs?.groups.orEmpty(),
                            selectedCount = selectedTabIds.size,
                            onSelectAll = { dispatch(TabsUiAction.BeginSelection(tabIds = tabs.filter { it.content.private == private }.mapTo(linkedSetOf()) { it.id })) },
                            onDeselectAll = { dispatch(TabsUiAction.BeginSelection()) },
                            onMoveToGroup = { dropTabs(selectedTabIds, TabDropTarget.Group(it.id)) },
                        )
                    } else {
                        Box(Modifier.size(48.dp)) {
                            ZapButton(appViewModel, fromScreen = "Tabs") { success ->
                                if (success) openIndividualTab(private)
                            }
                        }
                        IconButton(onClick = { dispatch(TabsUiAction.ToggleSearch) }) {
                            Icon(painterResource(R.drawable.icons_search), stringResource(R.string.browser_search_tabs))
                        }
                    }
                }

                TabsPageSelector(
                    selectedPage = uiState.page,
                    normalTabsCount = normalTabsCount,
                    privateTabsCount = privateTabsCount,
                    onPageSelected = { page ->
                        if (page != uiState.page) {
                            dispatch(TabsUiAction.SelectPage(page))
                        }
                    },
                )

                if (uiState.mode is TabsMode.Searching) {
                    val searchFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { query -> dispatch(TabsUiAction.UpdateSearch(query)) },
                        placeholder = { Text(stringResource(R.string.browser_search_tabs_hint)) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.icons_search), contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(onClick = { dispatch(TabsUiAction.ExitTransientMode) }) {
                                Icon(
                                    painterResource(R.drawable.icons_close),
                                    contentDescription = stringResource(R.string.browser_close_tab_search),
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .focusRequester(searchFocus),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                if (!restoreComplete || smartTabs == null || tabsViewOption == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val tabsGroupedString = stringResource(id = R.string.browser_tabs_grouped)
                val noTabsGroupedString = stringResource(id = R.string.browser_no_tabs_grouped)
                val tabGroupDeletedString = stringResource(id = R.string.browser_tab_group_deleted)
                val tabRemovedFromGroupString = stringResource(id = R.string.browser_tab_removed_from_group)


                groupBeingEdited?.let { group ->
                    EditTabGroupDialog(
                        group = group,
                        onDismiss = { groupBeingEdited = null },
                        onSave = { name, color ->
                            tabsViewModel.renameGroup(group.id, name)
                            tabsViewModel.updateGroupColor(group.id, color)
                            groupBeingEdited = null
                        }
                    )
                }

                groupBeingDeleted?.let { group ->
                    AlertDialog(
                        onDismissRequest = { groupBeingDeleted = null },
                        title = { Text(stringResource(R.string.browser_delete_tab_group)) },
                        text = { Text(stringResource(R.string.browser_delete_tab_group_message, group.name, group.tabs.size)) },
                        confirmButton = {
                            TextButton(onClick = {
                                val deleted = tabsViewModel.deleteGroup(group.id)
                                if (deleted > 0) {
                                    appViewModel.showSnackbar(tabGroupDeletedString.format(deleted))
                                }
                                if (expandedGroupId == group.id) expandedGroupId = null
                                groupBeingDeleted = null
                            }) { Text(stringResource(R.string.browser_delete_tab_group)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { groupBeingDeleted = null }) {
                                Text(stringResource(R.string.browser_cancel_selection))
                            }
                        }
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val readyTabs = smartTabs
                    if (restoreComplete && pageInitialized && readyTabs != null && tabsViewOption != null) {
                        AnimatedTabList(
                            smartTabs = readyTabs,
                            page = uiState.page,
                            searchActive = uiState.mode is TabsMode.Searching,
                            searchQuery = searchQuery,
                            onClose = onClose,
                            appViewModel = appViewModel,
                            tabsViewModel = tabsViewModel,
                            selectedTabId = selectedTabId,
                            tabsViewOption = resolvedTabsViewOption,
                            selectionMode = selectionMode,
                            selectedTabIds = selectedTabIds,
                            selectionTargetGroupId = selectionTargetGroupId,
                            onTabSelectionChange = { tabId ->
                                dispatch(TabsUiAction.ToggleTabSelection(tabId))
                            },
                            onEditGroup = { groupBeingEdited = it },
                            onAddTabsToGroup = { group ->
                                dispatch(TabsUiAction.BeginSelection(targetGroupId = group.id))
                            },
                            onDeleteGroup = { groupBeingDeleted = it },
                            onOpenGroup = { expandedGroupId = it.id },
                            expandedGroupId = expandedGroupId,
                            onCollapseGroup = { expandedGroupId = null },
                            onNewTabInGroup = { tabsViewModel.openNewTabInGroup(it.id) },
                            onSelectGroup = { group ->
                                if (selectionMode) {
                                    val ids = if (group.tabIds.all { it in selectedTabIds }) selectedTabIds - group.tabIds
                                        else selectedTabIds + group.tabIds
                                    dispatch(TabsUiAction.BeginSelection(tabIds = ids))
                                } else dispatch(TabsUiAction.BeginSelection(tabIds = group.tabIds))
                            },
                            onUngroup = { group ->
                                scope.launch {
                                    tabsViewModel.ungroup(group.id)?.let { change ->
                                        expandedGroupId = null
                                        dispatch(TabsUiAction.ExitTransientMode)
                                        showGroupingUndo(change, groupUngroupedString)
                                    }
                                }
                            },
                            onDragMoving = { dispatch(TabsUiAction.ExitTransientMode) },
                            onStartSelection = { dispatch(TabsUiAction.BeginSelection()) },
                            onTabsDropped = dropTabs,
                            onRemoveTabFromGroup = { groupId, tabId ->
                                if (tabsViewModel.removeTabFromGroup(groupId, tabId)) {
                                    appViewModel.showSnackbar(tabRemovedFromGroupString)
                                }
                            },
                            onTabLongPressed = { tab ->
                                dispatch(TabsUiAction.BeginSelection(tabIds = setOf(tab.id)))
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                if (selectionMode) {
                    SmartTabsActionBar(
                        private = private,
                        onCloseSelected = {
                            if (tabsViewModel.removeSelectedTabs(selectedTabIds, private) > 0) {
                                appViewModel.showTabClosureSnackbar(tabsClosedString)
                            }
                            dispatch(TabsUiAction.ExitTransientMode)
                        },
                        addingToGroup = selectionTargetGroupId != null,
                        selectedTabsCount = selectedTabIds.size,
                        onGroupTabs = {
                            val targetGroupId = selectionTargetGroupId
                            if (targetGroupId == null) {
                                val target = selectedTabIds.firstOrNull()
                                if (target != null) scope.launch {
                                    tabsViewModel.dropTabs(selectedTabIds - target, TabDropTarget.Tab(target), defaultGroupName)?.let { change ->
                                        expandedGroupId = change.groupId
                                        dispatch(TabsUiAction.ExitTransientMode)
                                        showGroupingUndo(change, groupCreatedString)
                                    }
                                }
                            } else {
                                val addedCount = tabsViewModel.addTabsToGroup(targetGroupId, selectedTabIds)
                                appViewModel.showSnackbar(
                                    if (addedCount > 0) {
                                        tabsAddedToGroupString.format(addedCount)
                                    } else {
                                        noTabsGroupedString
                                    }
                                )
                                if (addedCount > 0) {
                                    dispatch(TabsUiAction.ExitTransientMode)
                                }
                            }
                        },
                        onCancelTabSelection = {
                            dispatch(TabsUiAction.ExitTransientMode)
                        },
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        TabsLayoutButton(
                            resolvedTabsViewOption,
                            tabsViewModel::cycleTabsViewOption,
                            enabled = tabsViewOption != null,
                        )
                        IconButton(
                            enabled = !private && recentlyClosedCount > 0,
                            onClick = { if (tabsViewModel.reopenRecentlyClosed()) onClose() },
                        ) {
                            Icon(painterResource(R.drawable.icons_history), stringResource(R.string.browser_reopen_closed_tab))
                        }
                        FilledTonalButton(
                            onClick = { openIndividualTab(private) },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Icon(painterResource(R.drawable.icons_add_tab), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(if (private) R.string.browser_new_tab_private else R.string.browser_new_tab),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        TabsMenuMore(
                            private = private,
                            hasTabs = if (private) privateTabsCount > 0 else normalTabsCount > 0,
                            onSelectTabs = { dispatch(TabsUiAction.BeginSelection()) },
                            onRemoveTabs = {
                                tabsViewModel.removeTabs(private)
                                appViewModel.showTabClosureSnackbar(tabsClosedString)
                                dispatch(TabsUiAction.ExitTransientMode)
                            },
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun SmartTabsActionBar(
    private: Boolean,
    addingToGroup: Boolean,
    selectedTabsCount: Int,
    onGroupTabs: () -> Unit,
    onCloseSelected: () -> Unit,
    onCancelTabSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        TextButton(onClick = onCancelTabSelection, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.tab_tray_done))
        }
        if (!private) {
            FilledTonalButton(
                onClick = onGroupTabs,
                enabled = if (addingToGroup) selectedTabsCount >= 1 else selectedTabsCount >= 2,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(painterResource(R.drawable.icons_folder_add), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (addingToGroup) R.string.browser_add_to_group else R.string.browser_group_tabs))
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onCloseSelected, enabled = selectedTabsCount > 0) {
            Icon(painterResource(R.drawable.icons_close), stringResource(R.string.tab_tray_close_selected))
        }
    }
}

@Composable
private fun EditTabGroupDialog(
    group: SmartTabGroup,
    onDismiss: () -> Unit,
    onSave: (String, TabGroupColor) -> Unit
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var color by remember(group.id) { mutableStateOf(group.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_edit_tab_group)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.browser_tab_group_name_label)) },
                    modifier = Modifier.testTag("tab_group_name"),
                    singleLine = true
                )
                TabGroupColorSelector(selectedColor = color, onColorSelected = { color = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.browser_save_tab_group))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.browser_cancel_selection))
            }
        }
    )
}

@Composable
private fun TabGroupColorSelector(
    selectedColor: TabGroupColor,
    onColorSelected: (TabGroupColor) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.browser_tab_group_color),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            TabGroupColor.entries.forEach { color ->
                Box(
                    modifier = Modifier
                        .testTag("tab_group_color:${color.name}")
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color.toComposeColor(), CircleShape)
                        .then(
                            if (selectedColor == color) {
                                Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onColorSelected(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedColor == color) {
                        Icon(
                            painter = painterResource(R.drawable.icons_check),
                            contentDescription = stringResource(R.string.browser_tab_group_color_selected),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabsSelectionMenu(
    groups: List<SmartTabGroup>,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onMoveToGroup: (SmartTabGroup) -> Unit,
) {
    var show by remember { mutableStateOf(false) }
    var choosingGroup by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { show = true; choosingGroup = false }, modifier = Modifier.testTag("tabs_selection_menu")) {
            Icon(painterResource(R.drawable.icons_more_vertical), stringResource(R.string.menu_more_options))
        }
        DropdownMenu(expanded = show, onDismissRequest = { show = false }) {
            if (choosingGroup) {
                groups.forEach { group ->
                    DropdownMenuItem(text = { Text(group.name) }, modifier = Modifier.testTag("tabs_move_to_group:${group.id}"),
                        onClick = { show = false; onMoveToGroup(group) })
                }
            } else {
                if (groups.isNotEmpty()) DropdownMenuItem(text = { Text(stringResource(R.string.tabs_move_to_group)) },
                    enabled = selectedCount > 0, onClick = { choosingGroup = true })
                DropdownMenuItem(text = { Text(stringResource(R.string.tabs_select_all)) },
                    onClick = { show = false; onSelectAll() })
                DropdownMenuItem(text = { Text(stringResource(R.string.tabs_deselect_all)) },
                    enabled = selectedCount > 0, onClick = { show = false; onDeselectAll() })
            }
        }
    }
}

@Composable
private fun TabsMenuMore(
    private: Boolean,
    hasTabs: Boolean,
    onSelectTabs: () -> Unit,
    onRemoveTabs: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(painterResource(R.drawable.icons_more_vertical), stringResource(R.string.menu_more_options))
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tab_tray_select_tabs)) },
                leadingIcon = { Icon(painterResource(R.drawable.icons_checkbox_unchecked), null) },
                enabled = hasTabs,
                onClick = { showMenu = false; onSelectTabs() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (private) R.string.browser_close_private_tabs else R.string.browser_close_all_tabs)) },
                leadingIcon = { Icon(painterResource(R.drawable.icons_close), null) },
                enabled = hasTabs,
                onClick = { showMenu = false; onRemoveTabs() },
            )
        }
    }
}

@Composable
fun AnimatedTabList(
    smartTabs: SmartTabsState,
    page: TabsPage,
    searchActive: Boolean,
    searchQuery: String,
    onClose: () -> Unit,
    appViewModel: MidoriApplicationViewModel,
    tabsViewModel: TabsScreenViewModel,
    selectedTabId: String?,
    tabsViewOption: TabsViewOption,
    selectionMode: Boolean,
    selectedTabIds: Set<String>,
    selectionTargetGroupId: String?,
    onTabSelectionChange: (String) -> Unit,
    onEditGroup: (SmartTabGroup) -> Unit,
    onAddTabsToGroup: (SmartTabGroup) -> Unit,
    onDeleteGroup: (SmartTabGroup) -> Unit,
    onOpenGroup: (SmartTabGroup) -> Unit,
    onTabsDropped: (Set<String>, TabDropTarget) -> Unit,
    expandedGroupId: String?,
    onCollapseGroup: () -> Unit,
    onNewTabInGroup: (SmartTabGroup) -> Unit,
    onUngroup: (SmartTabGroup) -> Unit,
    onSelectGroup: (SmartTabGroup) -> Unit,
    onDragMoving: () -> Unit,
    onStartSelection: () -> Unit,
    onRemoveTabFromGroup: (String, String) -> Unit,
    onTabLongPressed: (TabSessionState) -> Unit
) {
    val normalTabs = remember(smartTabs) { smartTabs.forPageAndQuery(TabsPage.NORMAL, "") }
    val privateTabs = remember(smartTabs) { smartTabs.forPageAndQuery(TabsPage.PRIVATE, "") }
    fun initialIndex(tabs: SmartTabsState, layout: TabsViewOption) =
        tabs.selectedItemIndex(selectedTabId, layout).coerceAtLeast(0)
    val normalListState = rememberLazyListState(initialIndex(normalTabs, TabsViewOption.LIST))
    val privateListState = rememberLazyListState(initialIndex(privateTabs, TabsViewOption.LIST))
    val normalGridState = rememberLazyGridState(initialIndex(normalTabs, TabsViewOption.GRID))
    val privateGridState = rememberLazyGridState(initialIndex(privateTabs, TabsViewOption.GRID))
    val normalCarouselState = rememberLazyListState(initialIndex(normalTabs, TabsViewOption.CAROUSEL))
    val privateCarouselState = rememberLazyListState(initialIndex(privateTabs, TabsViewOption.CAROUSEL))
    val normalSearchCarouselState = rememberLazyListState()
    val privateSearchCarouselState = rememberLazyListState()
    val normalSearchListState = rememberLazyListState()
    val privateSearchListState = rememberLazyListState()
    val normalSearchGridState = rememberLazyGridState()
    val privateSearchGridState = rememberLazyGridState()
    var positionedViews by rememberSaveable(selectedTabId) { mutableStateOf(emptyList<String>()) }
    val tabClosedString = stringResource(id = R.string.browser_tab_closed)
    val onTabDeleted: (TabSessionState) -> Unit = { tab: SessionState ->
        tabsViewModel.removeTab(tab.id)
        appViewModel.showTabClosureSnackbar(tabClosedString)
    }

    // Changing privacy discards the outgoing collection before any layout animation can run.
    key(page) {
        val targetPage = page
        val pageState = remember(smartTabs, targetPage, searchQuery) {
            smartTabs.forPageAndQuery(targetPage, searchQuery)
        }
        val listState = when {
            searchActive && targetPage.isPrivate -> privateSearchListState
            searchActive -> normalSearchListState
            targetPage.isPrivate -> privateListState
            else -> normalListState
        }
        val gridState = when {
            searchActive && targetPage.isPrivate -> privateSearchGridState
            searchActive -> normalSearchGridState
            targetPage.isPrivate -> privateGridState
            else -> normalGridState
        }
        val carouselState = when {
            searchActive && targetPage.isPrivate -> privateSearchCarouselState
            searchActive -> normalSearchCarouselState
            targetPage.isPrivate -> privateCarouselState
            else -> normalCarouselState
        }
        TabsLayoutTransition(tabsViewOption) { layout ->
            val selectedIndex = remember(pageState, selectedTabId, layout) {
                pageState.selectedItemIndex(selectedTabId, layout)
            }
            val positionKey = "${page.name}/${layout.name}"

            LaunchedEffect(selectedTabId, layout, searchActive, selectionMode) {
                val interactionSource = when (layout) {
                    TabsViewOption.LIST -> listState.interactionSource
                    TabsViewOption.CAROUSEL -> carouselState.interactionSource
                    else -> gridState.interactionSource
                }
                interactionSource.interactions.collect { interaction ->
                    if (!searchActive && interaction is DragInteraction.Start && positionKey !in positionedViews) {
                        positionedViews = positionedViews + positionKey
                    }
                }
            }
            SideEffect {
                if (!searchActive && !selectionMode && positionKey !in positionedViews && selectedIndex >= 0) {
                    when (layout) {
                        TabsViewOption.LIST -> listState.requestScrollToItem(selectedIndex)
                        TabsViewOption.CAROUSEL -> carouselState.requestScrollToItem(selectedIndex)
                        else -> gridState.requestScrollToItem(selectedIndex)
                    }
                    positionedViews = positionedViews + positionKey
                }
            }
            val onTabSelected = { tab: SessionState ->
                if (selectionMode) {
                    onTabSelectionChange(tab.id)
                } else {
                    tabsViewModel.selectTab(tab.id)
                    onClose()
                }
            }

            if (searchQuery.isNotBlank() && pageState.isEmpty) {
                EmptyPagePlaceholder(
                    icon = R.drawable.icons_search,
                    title = stringResource(R.string.browser_tabs_search_empty_title),
                    subtitle = stringResource(R.string.browser_tabs_search_empty_subtitle, searchQuery),
                )
            } else {
                SmartTabView(
                    state = pageState,
                    private = targetPage.isPrivate,
                    selectedTabId = selectedTabId,
                    thumbnailStorage = tabsViewModel.thumbnailStorage,
                    browserIcons = tabsViewModel.browserIcons,
                    listState = listState,
                    gridState = gridState,
                    carouselState = carouselState,
                    modifier = Modifier.fillMaxSize(),
                    onTabSelected = onTabSelected,
                    onTabDeleted = onTabDeleted,
                    tabsViewOption = layout,
                    selectionMode = selectionMode,
                    selectedTabIds = selectedTabIds,
                    onTabSelectionChange = onTabSelectionChange,
                    onTabLongPressed = onTabLongPressed,
                    selectionTargetGroupId = selectionTargetGroupId,
                    onEditGroup = onEditGroup,
                    onAddTabsToGroup = onAddTabsToGroup,
                    onDeleteGroup = onDeleteGroup,
                    onOpenGroup = onOpenGroup,
                    onTabsDropped = onTabsDropped,
                    expandedGroupId = expandedGroupId,
                    onCollapseGroup = onCollapseGroup,
                    onNewTabInGroup = onNewTabInGroup,
                    onUngroup = onUngroup,
                    onSelectGroup = onSelectGroup,
                    onDragMoving = onDragMoving,
                    onStartSelection = onStartSelection,
                    onRemoveTabFromGroup = onRemoveTabFromGroup,
                )
            }
        }
    }
}

@Composable
internal fun TabsPageSelector(
    selectedPage: TabsPage,
    normalTabsCount: Int,
    privateTabsCount: Int,
    onPageSelected: (TabsPage) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            TabsPage.entries.forEach { page ->
                val selected = selectedPage == page
                val contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onPageSelected(page) })
                        .testTag("tabs_page_${page.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        if (page.isPrivate) Icon(painterResource(R.drawable.icons_privacy_mask), null, Modifier.size(18.dp), tint = contentColor)
                        Text(
                            text = stringResource(if (page.isPrivate) R.string.tab_tray_private_tabs else R.string.tab_tray_normal_tabs),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text(
                            text = (if (page.isPrivate) privateTabsCount else normalTabsCount).toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                        )
                    }
                    Box(
                        Modifier.fillMaxWidth(0.6f).height(3.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                            )
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
