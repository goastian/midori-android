package org.midorinext.android.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collect
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
    var showGroupNameDialog by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf("") }
    var groupColor by rememberSaveable { mutableStateOf(TabGroupColor.BLUE) }
    var groupBeingEdited by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingDeleted by remember { mutableStateOf<SmartTabGroup?>(null) }
    var groupBeingOpened by remember { mutableStateOf<SmartTabGroup?>(null) }

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
                    Box(Modifier.size(48.dp)) {
                        ZapButton(appViewModel, fromScreen = "Tabs") { success ->
                            if (success) openIndividualTab(private)
                        }
                    }
                    IconButton(onClick = { dispatch(TabsUiAction.ToggleSearch) }) {
                        Icon(painterResource(R.drawable.icons_search), stringResource(R.string.browser_search_tabs))
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
                val tabsAddedToGroupString = stringResource(id = R.string.browser_tabs_added_to_group)
                val tabGroupDeletedString = stringResource(id = R.string.browser_tab_group_deleted)
                val tabRemovedFromGroupString = stringResource(id = R.string.browser_tab_removed_from_group)

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
                    smartTabs?.groups?.firstOrNull { it.id == openedGroup.id }?.let { group ->
                        val tabClosedString = stringResource(id = R.string.browser_tab_closed)
                        TabGroupTabsSheet(
                            group = group,
                            selectedTabId = tabsViewModel.selectedTabId.collectAsStateWithLifecycle().value,
                            thumbnailStorage = tabsViewModel.thumbnailStorage,
                            browserIcons = tabsViewModel.browserIcons,
                            onDismissRequest = { groupBeingOpened = null },
                            selectionMode = selectionMode,
                            selectedTabIds = selectedTabIds,
                            onTabLongPressed = { tab ->
                                dispatch(TabsUiAction.BeginSelection(tabIds = setOf(tab.id)))
                            },
                            onTabSelected = { tab ->
                                if (selectionMode) {
                                    dispatch(TabsUiAction.ToggleTabSelection(tab.id))
                                } else {
                                    tabsViewModel.selectTab(tab.id)
                                    groupBeingOpened = null
                                    onClose()
                                }
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
                        Button(
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
    onTabsDroppedOnGroup: (SmartTabGroup, Set<String>) -> Unit,
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
                    onTabsDroppedOnGroup = onTabsDroppedOnGroup,
                    onRemoveTabFromGroup = onRemoveTabFromGroup,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TabsPageSelector(
    selectedPage: TabsPage,
    normalTabsCount: Int,
    privateTabsCount: Int,
    onPageSelected: (TabsPage) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TabsPage.entries.forEachIndexed { index, page ->
            SegmentedButton(
                selected = selectedPage == page,
                onClick = { onPageSelected(page) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TabsPage.entries.size),
                icon = {},
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("tabs_page_${page.name}"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (page.isPrivate) Icon(painterResource(R.drawable.icons_privacy_mask), null, Modifier.size(18.dp))
                    Text(
                        text = stringResource(if (page.isPrivate) R.string.tab_tray_private_tabs else R.string.tab_tray_normal_tabs),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = (if (page.isPrivate) privateTabsCount else normalTabsCount).toString(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
