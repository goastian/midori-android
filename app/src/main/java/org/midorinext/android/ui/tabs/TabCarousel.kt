package org.midorinext.android.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import kotlin.math.abs

/** The item order is shared with [SmartTabsState.selectedItemIndex]: groups, active, inactive. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCarousel(
    tabs: List<TabSessionState>,
    state: LazyListState,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    browserIcons: BrowserIcons,
    onTabSelected: (TabSessionState) -> Unit,
    onTabDeleted: (TabSessionState) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selectedTabIds: Set<String> = emptySet(),
    onTabLongPressed: (TabSessionState) -> Unit = {},
    groups: List<SmartTabGroup> = emptyList(),
    inactiveTabs: List<TabSessionState> = emptyList(),
    onOpenGroup: (SmartTabGroup) -> Unit = {},
    onEditGroup: (SmartTabGroup) -> Unit = {},
    onAddTabsToGroup: (SmartTabGroup) -> Unit = {},
    onDeleteGroup: (SmartTabGroup) -> Unit = {},
    dragEnabled: Boolean = false,
    draggingTabId: String? = null,
    dropTargetId: String? = null,
    onGroupBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onTabDragStarted: (TabSessionState) -> Unit = {},
    onTabDragPositionChanged: (Offset) -> Unit = {},
    onTabDragFinished: () -> Unit = {},
    onTabDragCancelled: () -> Unit = {}
) {
    val count = groups.size + tabs.size + inactiveTabs.size
    if (count == 0) return

    val centeredIndex by remember(state) {
        derivedStateOf {
            val layout = state.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { item ->
                abs(item.offset + item.size / 2 - center)
            }?.index ?: state.firstVisibleItemIndex
        }
    }

    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Keep the next page visible on phones and avoid oversized screenshots on tablets.
            val cardWidth = (maxWidth * 0.82f).coerceAtMost(420.dp)
            val sidePadding = ((maxWidth - cardWidth) / 2).coerceAtLeast(0.dp)
            val previewHeight = (maxHeight - 96.dp).coerceIn(0.dp, 520.dp)
            val pageModifier = Modifier.width(cardWidth)

            LazyRow(
                state = state,
                modifier = Modifier.fillMaxSize().testTag("tabs_carousel"),
                contentPadding = PaddingValues(horizontal = sidePadding, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                userScrollEnabled = draggingTabId == null,
                flingBehavior = rememberSnapFlingBehavior(
                    lazyListState = state,
                    snapPosition = SnapPosition.Center
                )
            ) {
                items(groups, key = { "carousel-group-${it.id}" }, contentType = { "group" }) { group ->
                    TabGroupCard(
                        group = group,
                        selected = group.tabs.any { it.id == selectedTabId },
                        thumbnailStorage = thumbnailStorage,
                        onOpen = { onOpenGroup(group) },
                        onEdit = { onEditGroup(group) },
                        onAddTabs = { onAddTabsToGroup(group) },
                        onDelete = { onDeleteGroup(group) },
                        isDropTarget = dropTargetId == group.id,
                        onBoundsChanged = { bounds -> onGroupBoundsChanged(group.id, bounds) },
                        modifier = pageModifier,
                        previewHeight = previewHeight
                    )
                }
                // Separate intervals preserve stable keys without copying the entire tab list.
                for ((prefix, section) in listOf("active" to tabs, "inactive" to inactiveTabs)) {
                    items(section, key = { "carousel-$prefix-${it.id}" }, contentType = { "tab" }) { tab ->
                        TabCard(
                            tab = tab,
                            selected = tab.id == selectedTabId,
                            thumbnailStorage = thumbnailStorage,
                            browserIcons = browserIcons,
                            onSelected = onTabSelected,
                            onDeleted = onTabDeleted,
                            selectionMode = selectionMode,
                            isSelectedForGrouping = tab.id in selectedTabIds,
                            onLongPressed = onTabLongPressed,
                            dragEnabled = dragEnabled,
                            isBeingDragged = draggingTabId == tab.id,
                            onDragStarted = onTabDragStarted,
                            onDragPositionChanged = onTabDragPositionChanged,
                            onDragFinished = onTabDragFinished,
                            onDragCancelled = onTabDragCancelled,
                            previewHeight = previewHeight,
                            modifier = pageModifier
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.browser_tab_carousel_position,
                    (centeredIndex + 1).coerceIn(1, count),
                    count
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (centeredIndex >= groups.size + tabs.size && inactiveTabs.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.browser_inactive_tabs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
