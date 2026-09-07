package org.midorinext.android.ui.tabs

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.midorinext.android.R
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.PrivacyMode
import org.midorinext.android.ui.browser.ToolbarAction
import org.midorinext.android.ui.preferences.TabsViewPreferenceSelector
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.ui.widgets.TabCounter
import org.midorinext.android.ui.widgets.YesNoDialog
import org.midorinext.android.ui.zap.ZapButton
import mozilla.components.browser.state.state.SessionState
import mozilla.components.browser.state.state.TabSessionState

@Composable
fun TabsScreen(
    onClose: () -> Unit = {},
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    tabsViewModel: TabsScreenViewModel = hiltViewModel()
) {
    val selectedTabIsPrivate by appViewModel.isPrivate.collectAsStateWithLifecycle()
    val tabs by tabsViewModel.tabs.collectAsStateWithLifecycle()
    val smartTabs by tabsViewModel.smartTabs.collectAsStateWithLifecycle()
    val tabsViewOption by tabsViewModel.tabsViewOption.collectAsStateWithLifecycle()
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
    val dispatch: (TabsUiAction) -> Unit = { action -> uiState = uiState.reduce(action) }
    var showGroupNameDialog by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupColor by rememberSaveable { mutableStateOf(TabGroupColor.BLUE) }
    var groupBeingEdited by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingDeleted by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingOpened by remember { mutableStateOf<SmartTabGroup?>(null) }

    // A protobuf value saved by an incompatible app version can be UNRECOGNIZED.
    // Never pass that sentinel to Compose, which tries to obtain its numeric value.
    val resolvedTabsViewOption = when (tabsViewOption) {
        TabsViewOption.LIST -> TabsViewOption.LIST
        else -> TabsViewOption.GRID
    }

    val normalTabsCount by remember(tabs) { derivedStateOf { tabs.count { !it.content.private } } }

    LaunchedEffect(restoreComplete, selectedTabId, tabs) {
        if (restoreComplete && !pageInitialized) {
            val restoredSelectionIsPrivate = tabs
                .firstOrNull { tab -> tab.id == selectedTabId }
                ?.content
                ?.private
                ?: selectedTabIsPrivate
            uiState = uiState.reduce(
                TabsUiAction.SelectPage(
                    if (restoredSelectionIsPrivate) TabsPage.PRIVATE else TabsPage.NORMAL,
                ),
            )
            pageInitialized = true
        }
    }

    LaunchedEffect(uiState.page) {
        appViewModel.setPrivacyMode(
            if (uiState.page == TabsPage.PRIVATE) PrivacyMode.PRIVATE else PrivacyMode.NORMAL,
        )
    }

    val openIndividualTab: (Boolean) -> Unit = { privateTab ->
        // Creating a tab is always an escape hatch from the grouping flow. In particular, this
        // prevents a pending "add to group" action from swallowing the next new tab.
        dispatch(TabsUiAction.ExitTransientMode)
        tabsViewModel.openNewTab(privateTab)
        onClose()
    }

    BackHandler(
        enabled = (groupBeingOpened != null || uiState.mode !is TabsMode.Browsing) &&
            !showGroupNameDialog &&
            groupBeingEdited == null &&
            groupBeingDeleted == null,
    ) {
        if (groupBeingOpened != null) {
            groupBeingOpened = null
            return@BackHandler
        }
        dispatch(TabsUiAction.ExitTransientMode)
    }

    DisposableEffect(true) {
        onDispose {
            appViewModel.setPrivacyMode(PrivacyMode.SELECTED_TAB_PRIVACY)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Text(
                text = when (val mode = uiState.mode) {
                    is TabsMode.Selecting -> stringResource(
                        R.string.browser_group_tabs_selected,
                        mode.tabIds.size,
                    )
                    is TabsMode.Searching -> stringResource(R.string.browser_search_tabs)
                    TabsMode.Browsing -> stringResource(
                        if (private) R.string.tab_tray_private_tabs else R.string.tab_tray_normal_tabs,
                    )
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, end = 208.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val privateBeforeClick = private
                ZapButton(appViewModel, fromScreen = "Tabs") { success ->
                    if (success) {
                        openIndividualTab(privateBeforeClick)
                    }
                }
                ToolbarAction(
                    onClick = {
                        if (uiState.mode is TabsMode.Searching) {
                            dispatch(TabsUiAction.ExitTransientMode)
                        } else {
                            dispatch(TabsUiAction.ToggleSearch)
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.icons_search),
                        contentDescription = stringResource(R.string.browser_search_tabs),
                    )
                }
                ToolbarAction(onClick = {
                    openIndividualTab(private)
                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.icons_add_tab),
                        contentDescription = stringResource(R.string.menu_action_add_tab),
                    )
                }
                val tabsClosedString = stringResource(id = R.string.browser_tabs_closed)
                TabsMenuMore(
                    tabsViewOption = resolvedTabsViewOption,
                    private = private,
                    onTabsViewOptionChange = { tabsViewModel.updateTabsViewOption(it) },
                    onRemoveTabs = {
                        tabsViewModel.removeTabs(private)
                        appViewModel.showTabClosureSnackbar(tabsClosedString)
                        if (private) {
                            dispatch(TabsUiAction.SelectPage(TabsPage.NORMAL))
                        } else {
                            tabsViewModel.openNewTab(false)
                            onClose()
                        }
                    }
                )
            }
        }

        TabsPageSelector(
            selectedPage = uiState.page,
            normalTabsCount = normalTabsCount,
            onPageSelected = { page ->
                if (page != uiState.page) {
                    dispatch(TabsUiAction.SelectPage(page))
                }
            },
        )

        if (uiState.mode is TabsMode.Searching) {
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        HorizontalDivider()

        if (!restoreComplete) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        val tabsGroupedString = stringResource(id = R.string.browser_tabs_grouped)
        val noTabsGroupedString = stringResource(id = R.string.browser_no_tabs_grouped)
        val tabsAddedToGroupString = stringResource(id = R.string.browser_tabs_added_to_group)
        val tabGroupDeletedString = stringResource(id = R.string.browser_tab_group_deleted)
        val tabRemovedFromGroupString = stringResource(id = R.string.browser_tab_removed_from_group)
        if (selectionMode) {
            SmartTabsActionBar(
                addingToGroup = selectionTargetGroupId != null,
                selectedTabsCount = selectedTabIds.size,
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
                            dispatch(TabsUiAction.ExitTransientMode)
                        }
                    }
                },
                onCancelTabSelection = {
                    dispatch(TabsUiAction.ExitTransientMode)
                },
            )
        }

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
                                dispatch(TabsUiAction.ExitTransientMode)
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

        groupBeingOpened?.let { openedGroup ->
            // Resolve against the latest state so closing a tab while this sheet is open updates
            // the group immediately instead of leaving a stale snapshot on screen.
            smartTabs.groups.firstOrNull { it.id == openedGroup.id }?.let { group ->
                val tabClosedString = stringResource(id = R.string.browser_tab_closed)
                TabGroupTabsSheet(
                    group = group,
                    selectedTabId = tabsViewModel.selectedTabId.collectAsStateWithLifecycle().value,
                    thumbnailStorage = tabsViewModel.thumbnailStorage,
                    browserIcons = tabsViewModel.browserIcons,
                    contentBlockerState = tabsViewModel.contentBlockerState,
                    onDismissRequest = { groupBeingOpened = null },
                    onTabSelected = { tab ->
                        tabsViewModel.selectTab(tab.id)
                        groupBeingOpened = null
                        onClose()
                    },
                    onTabDeleted = { tab ->
                        tabsViewModel.removeTab(tab.id)
                        appViewModel.showTabClosureSnackbar(tabClosedString)
                    }
                )
            } ?: run {
                groupBeingOpened = null
            }
        }

        AnimatedTabList(
            smartTabs = smartTabs,
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
            onOpenGroup = { groupBeingOpened = it },
            onTabsDroppedOnGroup = { group, tabIds ->
                val addedCount = tabsViewModel.addTabsToGroup(group.id, tabIds)
                if (addedCount > 0) {
                    appViewModel.showSnackbar(tabsAddedToGroupString.format(addedCount))
                    dispatch(TabsUiAction.ExitTransientMode)
                }
            },
            onRemoveTabFromGroup = { groupId, tabId ->
                if (tabsViewModel.removeTabFromGroup(groupId, tabId)) {
                    appViewModel.showSnackbar(tabRemovedFromGroupString)
                }
            },
            onTabLongPressed = { tab ->
                if (!tab.content.private) {
                    dispatch(TabsUiAction.BeginSelection(tabIds = setOf(tab.id)))
                }
            }
        )
    }
}

@Composable
fun SmartTabsActionBar(
    addingToGroup: Boolean,
    selectedTabsCount: Int,
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
        AssistChip(
            onClick = {},
            label = { Text(stringResource(R.string.browser_group_tabs_selected, selectedTabsCount)) },
        )
        AssistChip(
            onClick = onGroupTabs,
            enabled = if (addingToGroup) selectedTabsCount >= 1 else selectedTabsCount >= 2,
            leadingIcon = {
                Icon(painterResource(R.drawable.icons_folder_add), contentDescription = null)
            },
            label = {
                Text(stringResource(if (addingToGroup) R.string.browser_add_to_group else R.string.browser_group_tabs))
            },
        )
        AssistChip(
            onClick = onCancelTabSelection,
            label = { Text(stringResource(R.string.browser_cancel_selection)) },
        )
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
    onTabsViewOptionChange: (TabsViewOption) -> Unit,
    onRemoveTabs: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showViewOptionPopup by remember { mutableStateOf(false) }

    Box {
        ToolbarAction(
            onClick = { showMenu = true },
        ) {
            Icon(
                painter = painterResource(id = R.drawable.icons_more_vertical),
                contentDescription = stringResource(R.string.menu_more_options)
            )
        }
        Dropdown(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.defaultMinSize(minWidth = 112.dp)
        ) {
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
    onTabsDroppedOnGroup: (SmartTabGroup, Set<String>) -> Unit,
    onRemoveTabFromGroup: (String, String) -> Unit,
    onTabLongPressed: (TabSessionState) -> Unit
) {
    val normalListState = rememberLazyListState()
    val privateListState = rememberLazyListState()
    val normalGridState = rememberLazyGridState()
    val privateGridState = rememberLazyGridState()
    val normalSearchListState = rememberLazyListState()
    val privateSearchListState = rememberLazyListState()
    val normalSearchGridState = rememberLazyGridState()
    val privateSearchGridState = rememberLazyGridState()
    val animationsEnabled = ValueAnimator.areAnimatorsEnabled()
    val layoutDirection = LocalLayoutDirection.current
    val tabClosedString = stringResource(id = R.string.browser_tab_closed)
    val onTabDeleted: (TabSessionState) -> Unit = { tab: SessionState ->
        tabsViewModel.removeTab(tab.id)
        appViewModel.showTabClosureSnackbar(tabClosedString)
    }

    AnimatedContent(
        targetState = page,
        contentKey = { targetPage -> targetPage },
        transitionSpec = {
            if (!animationsEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val logicalEnd = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
                val enteringOffset = if (targetState == TabsPage.PRIVATE) logicalEnd else -logicalEnd
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    initialOffsetX = { width -> width * enteringOffset },
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(durationMillis = 220),
                    targetOffsetX = { width -> -width * enteringOffset },
                )
            }
        },
        label = "tabsPage",
        modifier = Modifier.fillMaxSize(),
    ) { targetPage ->
        val pageState = remember(smartTabs, targetPage, searchQuery) {
            smartTabs.forPageAndQuery(targetPage, searchQuery)
        }
        val onTabSelected = { tab: SessionState ->
            if (selectionMode && !targetPage.isPrivate) {
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
                listState = when {
                    searchActive && targetPage.isPrivate -> privateSearchListState
                    searchActive -> normalSearchListState
                    targetPage.isPrivate -> privateListState
                    else -> normalListState
                },
                gridState = when {
                    searchActive && targetPage.isPrivate -> privateSearchGridState
                    searchActive -> normalSearchGridState
                    targetPage.isPrivate -> privateGridState
                    else -> normalGridState
                },
                modifier = Modifier.fillMaxHeight(),
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = tabsViewModel.contentBlockerState,
                tabsViewOption = tabsViewOption,
                selectionMode = selectionMode,
                selectedTabIds = selectedTabIds,
                onTabSelectionChange = onTabSelectionChange,
                onTabLongPressed = onTabLongPressed,
                selectionTargetGroupId = selectionTargetGroupId,
                onEditGroup = onEditGroup,
                onAddTabsToGroup = onAddTabsToGroup,
                onDeleteGroup = onDeleteGroup,
                onOpenGroup = onOpenGroup,
                onTabsDroppedOnGroup = onTabsDroppedOnGroup,
                onRemoveTabFromGroup = onRemoveTabFromGroup,
            )
        }
    }
}

@Composable
private fun TabsPageSelector(
    selectedPage: TabsPage,
    normalTabsCount: Int,
    onPageSelected: (TabsPage) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilterChip(
            selected = selectedPage == TabsPage.NORMAL,
            onClick = { onPageSelected(TabsPage.NORMAL) },
            label = { Text(stringResource(R.string.tab_tray_normal_tabs)) },
            leadingIcon = {
                Box(modifier = Modifier.size(24.dp)) {
                    TabCounter(tabCount = normalTabsCount)
                }
            },
            modifier = Modifier
                .weight(1f),
        )
        FilterChip(
            selected = selectedPage == TabsPage.PRIVATE,
            onClick = { onPageSelected(TabsPage.PRIVATE) },
            label = { Text(stringResource(R.string.tab_tray_private_tabs)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.icons_privacy_mask),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            modifier = Modifier
                .weight(1f),
        )
    }
}
