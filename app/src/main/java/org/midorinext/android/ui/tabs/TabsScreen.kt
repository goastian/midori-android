package org.midorinext.android.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.PrivacyMode
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.browser.TabOpening
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.zap.ZapButton
import org.midorinext.android.ui.preferences.TabsViewPreferenceSelector
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.ui.widgets.TabCounter
import org.midorinext.android.ui.widgets.YesNoDialog
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabsScreen(
    onClose: (openNewTab: TabOpening) -> Unit = {},
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    tabsViewModel: TabsScreenViewModel = hiltViewModel()
) {
    val private by appViewModel.isPrivate.collectAsState()
    val tabs by tabsViewModel.tabs.collectAsState()
    val smartTabs by tabsViewModel.smartTabs.collectAsState()
    val tabSearchQuery by tabsViewModel.tabSearchQuery.collectAsState()
    val tabsViewOption by tabsViewModel.tabsViewOption.collectAsState()
    val restoreComplete by tabsViewModel.restoreComplete.collectAsState()
    val canUndoClose by tabsViewModel.canUndoClose.collectAsState()
    val recentlyClosedCount by tabsViewModel.recentlyClosedCount.collectAsState()
    var selectedTabIds by remember { mutableStateOf(emptySet<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectionTargetGroupId by remember { mutableStateOf<String?>(null) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var groupColor by remember { mutableStateOf(TabGroupColor.BLUE) }
    var groupBeingEdited by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingDeleted by remember { mutableStateOf<SmartTabGroup?>(null) }

    // A protobuf value saved by an incompatible app version can be UNRECOGNIZED.
    // Never pass that sentinel to Compose, which tries to obtain its numeric value.
    val resolvedTabsViewOption = when (tabsViewOption) {
        TabsViewOption.LIST -> TabsViewOption.LIST
        else -> TabsViewOption.GRID
    }

    val normalTabsCount by remember(tabs) { derivedStateOf { tabs.count { !it.content.private } } }

    BackHandler(enabled = !showGroupNameDialog && groupBeingEdited == null && groupBeingDeleted == null) {
        if (selectionMode) {
            selectedTabIds = emptySet()
            selectionMode = false
            selectionTargetGroupId = null
        } else {
            onClose(TabOpening.NONE)
        }
    }

    DisposableEffect(true) {
        onDispose {
            appViewModel.setPrivacyMode(PrivacyMode.SELECTED_TAB_PRIVACY)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background))  {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)) {
            Row(modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
            ) {
                TabIconButton(
                    onClick = { appViewModel.setPrivacyMode(PrivacyMode.NORMAL) },
                    icon = {
                        Box(modifier = Modifier.size(30.dp)) {
                            TabCounter(tabCount = normalTabsCount)
                        }
                    },
                    selected = !private,
                    modifier = Modifier.size(48.dp, 56.dp)
                )
                TabIconButton(
                    onClick = { appViewModel.setPrivacyMode(PrivacyMode.PRIVATE) },
                    icon = { Icon(painter = painterResource(id = R.drawable.icons_privacy_mask), contentDescription = "Tabs") },
                    selected = private,
                    modifier = Modifier.size(48.dp, 56.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val privateBeforeClick = private
                ZapButton(appViewModel, fromScreen = "Tabs") { success ->
                    if (success) {
                        tabsViewModel.openNewTab(privateBeforeClick)
                        onClose(TabOpening.NONE)
                    }
                }
                ToolbarAction(onClick = {
                    tabsViewModel.openNewTab(private)
                    onClose(TabOpening.NONE)
                }) {
                    Icon(painter = painterResource(id = R.drawable.icons_add_tab), contentDescription = "Add tab")
                }
                val tabsClosedString = stringResource(id = R.string.browser_tabs_closed)
                val duplicateTabsClosedString = stringResource(id = R.string.browser_duplicate_tabs_closed)
                val noDuplicateTabsString = stringResource(id = R.string.browser_no_duplicate_tabs)
                val tabReopenedString = stringResource(id = R.string.browser_recent_tab_reopened)
                TabsMenuMore(
                    tabsViewOption = resolvedTabsViewOption,
                    private = private,
                    canUndoClose = canUndoClose,
                    recentlyClosedCount = recentlyClosedCount,
                    onTabsViewOptionChange = { tabsViewModel.updateTabsViewOption(it) },
                    onUndoClose = {
                        tabsViewModel.undoLastClose()
                        appViewModel.showSnackbar(tabReopenedString)
                    },
                    onReopenRecentlyClosed = {
                        if (tabsViewModel.reopenRecentlyClosed()) {
                            appViewModel.showSnackbar(tabReopenedString)
                        }
                    },
                    onCloseDuplicateTabs = {
                        val closedCount = tabsViewModel.closeDuplicateTabs(private)
                        appViewModel.showSnackbar(
                            if (closedCount > 0) {
                                duplicateTabsClosedString.format(closedCount)
                            } else {
                                noDuplicateTabsString
                            }
                        )
                    },
                    onStartTabSelection = {
                        selectionMode = true
                        selectionTargetGroupId = null
                        selectedTabIds = setOfNotNull(tabsViewModel.selectedTabId.value)
                    },
                    onRemoveTabs = {
                        tabsViewModel.removeTabs(private)
                        appViewModel.showSnackbar(tabsClosedString)
                        if (private) {
                            appViewModel.setPrivacyMode(PrivacyMode.NORMAL)
                        } else {
                            tabsViewModel.openNewTab(false)
                            onClose(TabOpening.NONE)
                        }
                    }
                )
            }
        }

        HorizontalDivider()

        if (!restoreComplete) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        TabSearchField(
            query = tabSearchQuery,
            onQueryChange = tabsViewModel::updateTabSearchQuery,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        val duplicateTabsClosedString = stringResource(id = R.string.browser_duplicate_tabs_closed)
        val noDuplicateTabsString = stringResource(id = R.string.browser_no_duplicate_tabs)
        val tabsGroupedString = stringResource(id = R.string.browser_tabs_grouped)
        val noTabsGroupedString = stringResource(id = R.string.browser_no_tabs_grouped)
        val tabsAddedToGroupString = stringResource(id = R.string.browser_tabs_added_to_group)
        val tabGroupDeletedString = stringResource(id = R.string.browser_tab_group_deleted)
        val tabRemovedFromGroupString = stringResource(id = R.string.browser_tab_removed_from_group)
        val tabReopenedString = stringResource(id = R.string.browser_recent_tab_reopened)
        SmartTabsActionBar(
            private = private,
            canReopenClosedTab = canUndoClose || recentlyClosedCount > 0,
            onReopenClosedTab = {
                if (canUndoClose) {
                    tabsViewModel.undoLastClose()
                    appViewModel.showSnackbar(tabReopenedString)
                } else if (tabsViewModel.reopenRecentlyClosed()) {
                    appViewModel.showSnackbar(tabReopenedString)
                }
            },
            onCloseDuplicateTabs = {
                val closedCount = tabsViewModel.closeDuplicateTabs(private)
                appViewModel.showSnackbar(
                    if (closedCount > 0) {
                        duplicateTabsClosedString.format(closedCount)
                    } else {
                        noDuplicateTabsString
                    }
                )
            },
            selectionMode = selectionMode,
            addingToGroup = selectionTargetGroupId != null,
            selectedTabsCount = selectedTabIds.size,
            onStartTabSelection = {
                selectionMode = true
                selectionTargetGroupId = null
                selectedTabIds = setOfNotNull(tabsViewModel.selectedTabId.value)
            },
            onGroupTabs = {
                val targetGroupId = selectionTargetGroupId
                if (targetGroupId == null) {
                    groupName = tabsViewModel.nextGroupName()
                    groupColor = tabsViewModel.nextGroupColor()
                    showGroupNameDialog = true
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
                        selectedTabIds = emptySet()
                        selectionMode = false
                        selectionTargetGroupId = null
                    }
                }
            },
            onCancelTabSelection = {
                selectedTabIds = emptySet()
                selectionMode = false
                selectionTargetGroupId = null
            }
        )

        if (showGroupNameDialog) {
            AlertDialog(
                onDismissRequest = { showGroupNameDialog = false },
                title = { Text(stringResource(R.string.browser_name_tab_group)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text(stringResource(R.string.browser_tab_group_name_label)) },
                            singleLine = true
                        )
                        TabGroupColorSelector(selectedColor = groupColor, onColorSelected = { groupColor = it })
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = groupName.isNotBlank(),
                        onClick = {
                            val groupedCount = tabsViewModel.groupTabs(selectedTabIds, groupName, groupColor)
                            appViewModel.showSnackbar(
                                if (groupedCount > 0) {
                                    tabsGroupedString.format(groupedCount)
                                } else {
                                    noTabsGroupedString
                                }
                            )
                            if (groupedCount > 0) {
                                selectedTabIds = emptySet()
                                selectionMode = false
                            }
                            showGroupNameDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.browser_create_tab_group))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGroupNameDialog = false }) {
                        Text(stringResource(R.string.browser_cancel_selection))
                    }
                }
            )
        }

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

        AnimatedTabList(
            smartTabs = smartTabs,
            private = private,
            onClose = onClose,
            appViewModel = appViewModel,
            tabsViewModel = tabsViewModel,
            tabsViewOption = resolvedTabsViewOption,
            selectionMode = selectionMode,
            selectedTabIds = selectedTabIds,
            selectionTargetGroupId = selectionTargetGroupId,
            onTabSelectionChange = { tabId ->
                selectedTabIds = selectedTabIds.let { selected ->
                    if (tabId in selected) selected - tabId else selected + tabId
                }
            },
            onEditGroup = { groupBeingEdited = it },
            onAddTabsToGroup = { group ->
                selectionMode = true
                selectionTargetGroupId = group.id
                selectedTabIds = emptySet()
            },
            onDeleteGroup = { groupBeingDeleted = it },
            onRemoveTabFromGroup = { groupId, tabId ->
                if (tabsViewModel.removeTabFromGroup(groupId, tabId)) {
                    appViewModel.showSnackbar(tabRemovedFromGroupString)
                }
            }
        )
    }
}

@Composable
fun SmartTabsActionBar(
    private: Boolean,
    canReopenClosedTab: Boolean,
    selectionMode: Boolean,
    addingToGroup: Boolean,
    selectedTabsCount: Int,
    onReopenClosedTab: () -> Unit,
    onCloseDuplicateTabs: () -> Unit,
    onStartTabSelection: () -> Unit,
    onGroupTabs: () -> Unit,
    onCancelTabSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
    ) {
        if (selectionMode) {
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.browser_group_tabs_selected, selectedTabsCount)) }
            )
            AssistChip(
                onClick = onGroupTabs,
                enabled = if (addingToGroup) selectedTabsCount >= 1 else selectedTabsCount >= 2,
                leadingIcon = {
                    Icon(painterResource(R.drawable.icons_folder_add), contentDescription = null)
                },
                label = {
                    Text(stringResource(if (addingToGroup) R.string.browser_add_to_group else R.string.browser_group_tabs))
                }
            )
            AssistChip(
                onClick = onCancelTabSelection,
                label = { Text(stringResource(R.string.browser_cancel_selection)) }
            )
            return@Row
        }
        AssistChip(
            onClick = onReopenClosedTab,
            enabled = canReopenClosedTab,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.icons_reload),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.browser_reopen_closed_tab)) }
        )
        AssistChip(
            onClick = onCloseDuplicateTabs,
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.icons_close_circled),
                    contentDescription = null
                )
            },
            label = { Text(stringResource(id = R.string.browser_close_duplicate_tabs)) }
        )
        if (!private) {
            AssistChip(
                onClick = onStartTabSelection,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.icons_folder_add),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(id = R.string.browser_group_tabs)) }
            )
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
fun TabsMenuMore(
    tabsViewOption: TabsViewOption,
    private: Boolean,
    canUndoClose: Boolean,
    recentlyClosedCount: Int,
    onTabsViewOptionChange: (TabsViewOption) -> Unit,
    onUndoClose: () -> Unit,
    onReopenRecentlyClosed: () -> Unit,
    onCloseDuplicateTabs: () -> Unit,
    onStartTabSelection: () -> Unit,
    onRemoveTabs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showViewOptionPopup by remember { mutableStateOf(false) }

    Box {
        ToolbarAction(onClick = { showMenu = true }) {
            Icon(
                painter = painterResource(id = R.drawable.icons_more_vertical),
                contentDescription = "More"
            )
        }
        Dropdown(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.defaultMinSize(minWidth = 112.dp)
        ) {
            DropdownItem(
                text = stringResource(id = R.string.browser_reopen_closed_tab),
                icon = R.drawable.icons_reload,
                onClick = {
                    showMenu = false
                    if (canUndoClose) {
                        onUndoClose()
                    } else if (recentlyClosedCount > 0) {
                        onReopenRecentlyClosed()
                    }
                }
            )
            DropdownItem(
                text = stringResource(id = R.string.browser_close_duplicate_tabs),
                icon = R.drawable.icons_close_circled,
                onClick = {
                    showMenu = false
                    onCloseDuplicateTabs()
                }
            )
            if (!private) {
                DropdownItem(
                    text = stringResource(id = R.string.browser_group_tabs),
                    icon = R.drawable.icons_folder_add,
                    onClick = {
                        showMenu = false
                        onStartTabSelection()
                    }
                )
            }
            DropdownItem(
                text = stringResource(id = if (private) R.string.browser_close_private_tabs else R.string.browser_close_all_tabs),
                icon = R.drawable.icons_close,
                onClick = {
                    showMenu = false
                    onRemoveTabs()
                }
            )
            DropdownItem(
                text = stringResource(id = R.string.tabs_view_label),
                icon = R.drawable.icons_grid,
                onClick = {
                    showMenu = false
                    showViewOptionPopup = true
                }
            )
        }
    }
    if (showViewOptionPopup) {
        val originalOption = remember { tabsViewOption }
        YesNoDialog(
            onDismissRequest = { showViewOptionPopup = false },
            onYes = { showViewOptionPopup = false },
            onNo = {
                onTabsViewOptionChange(originalOption)
                showViewOptionPopup = false
            },
            title = stringResource(id = R.string.tabs_view_label),
            additionalContent = {
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    TabsViewPreferenceSelector(
                        value = tabsViewOption,
                        onValueChange = onTabsViewOptionChange
                    )
                }
            }
        )
    }
}

@Composable
fun AnimatedTabList(
    smartTabs: SmartTabsState,
    private: Boolean,
    onClose: (openNewTab: TabOpening) -> Unit,
    appViewModel: MidoriApplicationViewModel,
    tabsViewModel: TabsScreenViewModel,
    tabsViewOption: TabsViewOption,
    selectionMode: Boolean,
    selectedTabIds: Set<String>,
    selectionTargetGroupId: String?,
    onTabSelectionChange: (String) -> Unit,
    onEditGroup: (SmartTabGroup) -> Unit,
    onAddTabsToGroup: (SmartTabGroup) -> Unit,
    onDeleteGroup: (SmartTabGroup) -> Unit,
    onRemoveTabFromGroup: (String, String) -> Unit
) {
    val selectedTabId by tabsViewModel.selectedTabId.collectAsState()

    Box(Modifier.fillMaxSize()) {
        val onTabSelected = { tab: SessionState ->
            if (selectionMode && !private) {
                onTabSelectionChange(tab.id)
            } else {
                tabsViewModel.selectTab(tab.id)
                onClose(TabOpening.NONE)
            }
        }
        val tabClosedString = stringResource(id = R.string.browser_tab_closed)
        val onTabDeleted: (TabSessionState) -> Unit = { tab: SessionState ->
            tabsViewModel.removeTab(tab.id)
            appViewModel.showSnackbar(tabClosedString)
        }

        AnimatedVisibility(
            visible = private,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it })
        ) {
            SmartTabView(
                state = smartTabs,
                private = private,
                selectedTabId = selectedTabId,
                thumbnailStorage = tabsViewModel.thumbnailStorage,
                browserIcons = tabsViewModel.browserIcons,
                modifier = Modifier.fillMaxHeight(),
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = tabsViewModel.contentBlockerState,
                tabsViewOption = tabsViewOption,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                selectionTargetGroupId = selectionTargetGroupId
            )
        }

        AnimatedVisibility(
            visible = !private,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            SmartTabView(
                state = smartTabs,
                private = private,
                selectedTabId = selectedTabId,
                thumbnailStorage = tabsViewModel.thumbnailStorage,
                browserIcons = tabsViewModel.browserIcons,
                modifier = Modifier.fillMaxHeight(),
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = tabsViewModel.contentBlockerState,
                tabsViewOption = tabsViewOption,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                selectionTargetGroupId = selectionTargetGroupId,
                onEditGroup = onEditGroup,
                onAddTabsToGroup = onAddTabsToGroup,
                onDeleteGroup = onDeleteGroup,
                onRemoveTabFromGroup = onRemoveTabFromGroup
            )
        }
    }
}

@Composable
fun TabSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(painterResource(id = R.drawable.icons_search), contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(id = R.drawable.icons_close), contentDescription = null)
                }
            }
        },
        placeholder = { Text(stringResource(id = R.string.browser_search_tabs_hint)) }
    )
}

@Composable
fun TabIconButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier
        .minimumInteractiveComponentSize()
        .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.align(Alignment.Center)) {
            val contentColor = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                icon()
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            AnimatedVisibility(visible = selected) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
