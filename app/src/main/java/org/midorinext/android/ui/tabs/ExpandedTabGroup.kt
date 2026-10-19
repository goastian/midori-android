package org.midorinext.android.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R

@Composable
internal fun GroupCount(group: SmartTabGroup) {
    Surface(color = group.color.toComposeColor(), contentColor = androidx.compose.ui.graphics.Color.White,
        shape = CircleShape, modifier = Modifier.size(24.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(group.tabs.size.toString(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun ExpandedTabGroup(
    group: SmartTabGroup,
    thumbnailStorage: ThumbnailStorage,
    modifier: Modifier,
    carousel: Boolean,
    previewHeight: Dp?,
    selectionMode: Boolean,
    dragging: Boolean,
    onCollapse: () -> Unit,
    onNewTab: () -> Unit,
    onUngroup: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    tabContent: @Composable (TabSessionState, Modifier, Dp?) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(modifier.testTag("tab_group_expanded:${group.id}"), shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GroupCount(group)
                Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onCollapse, modifier = Modifier.testTag("tab_group_collapse:${group.id}")) {
                    Icon(painterResource(R.drawable.icons_arrow_up), stringResource(R.string.tabs_collapse_group))
                }
                if (!selectionMode) {
                    IconButton(onClick = onNewTab, modifier = Modifier.testTag("tab_group_new_tab:${group.id}")) {
                        Icon(painterResource(R.drawable.icons_add_tab), stringResource(R.string.tabs_new_tab_in_group))
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("tab_group_menu:${group.id}")) {
                        Icon(painterResource(R.drawable.icons_more_vertical), stringResource(R.string.browser_manage_tab_group))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.tabs_ungroup)) },
                            onClick = { menu = false; onUngroup() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.browser_edit_tab_group)) },
                            leadingIcon = { Icon(painterResource(R.drawable.icons_edit), null) },
                            onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.tab_tray_select_tabs)) },
                            leadingIcon = { Icon(painterResource(R.drawable.icons_checkbox_unchecked), null) },
                            onClick = { menu = false; onSelect() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.browser_close_all_tabs)) },
                            leadingIcon = { Icon(painterResource(R.drawable.icons_close), null) },
                            onClick = { menu = false; onClose() })
                    }
                }
            }
            if (carousel) {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    val width = maxWidth - 40.dp
                    val state = rememberLazyListState(initialFirstVisibleItemIndex = group.tabs.lastIndex)
                    var previousCount by remember { mutableIntStateOf(group.tabs.size) }
                    LaunchedEffect(group.tabs.size) {
                        if (group.tabs.size > previousCount) state.animateScrollToItem(group.tabs.lastIndex)
                        previousCount = group.tabs.size
                    }
                    LazyRow(state = state, userScrollEnabled = !dragging, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().testTag("tab_group_tabs:${group.id}")) {
                        items(group.tabs, key = { it.id }) { tab -> tabContent(tab, Modifier.width(width), previewHeight) }
                    }
                }
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    val columns = (maxWidth / 150.dp).toInt().coerceAtLeast(1)
                    val itemWidth = (maxWidth - 12.dp * (columns - 1)) / columns
                    val rows = (group.tabs.size + columns - 1) / columns
                    val height = ((itemWidth / 0.76f + 60.dp) * rows + 12.dp * rows)
                        .coerceAtMost(LocalConfiguration.current.screenHeightDp.dp * 0.65f)
                    LazyVerticalGrid(columns = GridCells.Fixed(columns),
                        userScrollEnabled = !dragging,
                        modifier = Modifier.fillMaxWidth().height(height).testTag("tab_group_tabs:${group.id}"),
                        verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)) {
                        gridItems(group.tabs, key = { it.id }) { tab -> tabContent(tab, Modifier, null) }
                    }
                }
            }
        }
    }
}
