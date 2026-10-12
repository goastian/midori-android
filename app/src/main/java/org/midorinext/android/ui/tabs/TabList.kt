package org.midorinext.android.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage


@Composable
fun TabList(
    tabs: List<TabSessionState>,
    state: LazyListState,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    onTabSelected: (tab: TabSessionState) -> Unit,
    onTabDeleted: (tab: TabSessionState) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabSelectionChange: (String) -> Unit = {},
    onTabLongPressed: (TabSessionState) -> Unit = {}
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = modifier.fillMaxWidth().testTag("tabs_list")
    ) {
        itemsIndexed(
            items = tabs,
            key = { _, tab -> tab.id },
            contentType = { _, _ -> "tab" },
        ) { _, tab ->
            TabRow(
                tab = tab,
                selected = tab.id == selectedTabId,
                thumbnailStorage = thumbnailStorage,
                onSelected = onTabSelected,
                onDeleted = onTabDeleted,
                selectionMode = selectionMode,
                isSelectedForGrouping = tab.id in selectedTabIds,
                onLongPressed = onTabLongPressed
            )

        }
    }
}
