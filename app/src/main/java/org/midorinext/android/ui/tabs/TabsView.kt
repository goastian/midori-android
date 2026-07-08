package org.midorinext.android.ui.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.contentBlocker.ContentBlockerState

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
    tabsViewOption: TabsViewOption = TabsViewOption.LIST
) {
    val searchResults = remember(state.searchResults, private) {
        state.searchResults.filter { it.content.private == private }
    }
    val activeTabs = remember(state.activeTabs, private) {
        state.activeTabs.filter { it.content.private == private }
    }
    val inactiveTabs = remember(state.inactiveTabs, private) {
        state.inactiveTabs.filter { it.content.private == private }
    }
    val groups = remember(state.groups, private) {
        if (private) emptyList() else state.groups
    }

    if (state.searchActive) {
        return TabView(
            tabs = searchResults,
            private = private,
            selectedTabId = selectedTabId,
            thumbnailStorage = thumbnailStorage,
            browserIcons = browserIcons,
            onTabSelected = onTabSelected,
            onTabDeleted = onTabDeleted,
            contentBlockerState = contentBlockerState,
            modifier = modifier,
            tabsViewOption = tabsViewOption
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
            tabsViewOption = tabsViewOption
        )
    }

    if (activeTabs.isEmpty() && inactiveTabs.isEmpty() && groups.isEmpty()) {
        EmptyTabsPlaceholder(private)
        return
    }

    LazyColumn(modifier = modifier) {
        groups.forEach { group ->
            item(key = "group-${group.id}") {
                TabSectionHeader(
                    title = group.name,
                    subtitle = stringResource(R.string.browser_tab_group_count, group.tabs.size)
                )
            }
            items(group.tabs, key = { "group-${group.id}-${it.id}" }) { tab ->
                TabRow(
                    tab = tab,
                    selected = tab.id == selectedTabId,
                    thumbnailStorage = thumbnailStorage,
                    onSelected = onTabSelected,
                    onDeleted = onTabDeleted,
                    contentBlockerState = contentBlockerState
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
                    contentBlockerState = contentBlockerState
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
                    contentBlockerState = contentBlockerState
                )
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
    tabsViewOption: TabsViewOption = TabsViewOption.LIST
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
                modifier = modifier
            )
            TabsViewOption.GRID -> TabGrid(
                tabs = tabs,
                selectedTabId = selectedTabId,
                thumbnailStorage = thumbnailStorage,
                browserIcons = browserIcons,
                onTabSelected = onTabSelected,
                onTabDeleted = onTabDeleted,
                contentBlockerState = contentBlockerState,
                modifier = modifier
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
