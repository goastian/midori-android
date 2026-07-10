package org.midorinext.android.ui.tabs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    val normalTabsCount by remember(tabs) { derivedStateOf { tabs.count { !it.content.private } } }

    BackHandler {
        onClose(TabOpening.NONE)
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
                val tabsGroupedString = stringResource(id = R.string.browser_tabs_grouped)
                val noTabsGroupedString = stringResource(id = R.string.browser_no_tabs_grouped)
                val tabReopenedString = stringResource(id = R.string.browser_recent_tab_reopened)
                TabsMenuMore(
                    tabsViewOption = tabsViewOption,
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
                    onGroupTabsBySite = {
                        val groupedCount = tabsViewModel.groupTabsBySite(private)
                        appViewModel.showSnackbar(
                            if (groupedCount > 0) {
                                tabsGroupedString.format(groupedCount)
                            } else {
                                noTabsGroupedString
                            }
                        )
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
            onGroupTabsBySite = {
                val groupedCount = tabsViewModel.groupTabsBySite(private)
                appViewModel.showSnackbar(
                    if (groupedCount > 0) {
                        tabsGroupedString.format(groupedCount)
                    } else {
                        noTabsGroupedString
                    }
                )
            }
        )

        AnimatedTabList(
            smartTabs = smartTabs,
            private = private,
            onClose = onClose,
            appViewModel = appViewModel,
            tabsViewModel = tabsViewModel,
            tabsViewOption = tabsViewOption
        )
    }
}

@Composable
fun SmartTabsActionBar(
    private: Boolean,
    canReopenClosedTab: Boolean,
    onReopenClosedTab: () -> Unit,
    onCloseDuplicateTabs: () -> Unit,
    onGroupTabsBySite: () -> Unit,
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
                onClick = onGroupTabsBySite,
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.icons_folder_add),
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(id = R.string.browser_group_tabs_by_site)) }
            )
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
    onGroupTabsBySite: () -> Unit,
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
                    text = stringResource(id = R.string.browser_group_tabs_by_site),
                    icon = R.drawable.icons_folder_add,
                    onClick = {
                        showMenu = false
                        onGroupTabsBySite()
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
    tabsViewOption: TabsViewOption
) {
    val selectedTabId by tabsViewModel.selectedTabId.collectAsState()

    Box(Modifier.fillMaxSize()) {
        val onTabSelected = { tab: SessionState ->
            tabsViewModel.selectTab(tab.id)
            onClose(TabOpening.NONE)
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
                tabsViewOption = tabsViewOption
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
                tabsViewOption = tabsViewOption
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
