package org.midorinext.android.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import org.midorinext.android.contentBlocker.ContentBlockerState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage


@Composable
fun TabList(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    thumbnailStorage: ThumbnailStorage,
    onTabSelected: (tab: TabSessionState) -> Unit,
    onTabDeleted: (tab: TabSessionState) -> Unit,
    contentBlockerState: ContentBlockerState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { _, tab ->
            TabRow(
                tab = tab,
                selected = tab.id == selectedTabId,
                thumbnailStorage = thumbnailStorage,
                onSelected = onTabSelected,
                onDeleted = onTabDeleted,
                contentBlockerState = contentBlockerState
            )

            /*
            val currentTab by rememberUpdatedState(newValue = tab)
            val dismissState = rememberDismissState(
                confirmValueChange = { dismissValue ->
                    var result = true
                    if (dismissValue == DismissValue.DismissedToStart) {
                        onTabDeleted(currentTab)
                        result = false
                    } else if (dismissValue == DismissValue.DismissedToEnd) {
                        // TODO bookmark tab !
                        result = false
                    }
                    result
                },
                positionalThreshold = { 92.dp.toPx() } // { it / 3 }
            )

            val haptic = LocalHapticFeedback.current
            LaunchedEffect(key1 = dismissState.targetValue) {
                if (dismissState.targetValue != DismissValue.Default) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            }

            SwipeToDismiss(
                state = dismissState,
                background = {
                    val color by animateColorAsState(
                        when (dismissState.targetValue) {
                            DismissValue.Default -> MaterialTheme.colorScheme.secondaryContainer
                            DismissValue.DismissedToEnd -> MaterialTheme.colorScheme.primaryContainer
                            DismissValue.DismissedToStart -> MaterialTheme.colorScheme.error
                        }, label = "tabSwipeColor"
                    )
                    val iconSize by animateDpAsState(
                        targetValue = when (dismissState.targetValue) {
                            DismissValue.Default -> 24.dp
                            DismissValue.DismissedToEnd -> 36.dp
                            DismissValue.DismissedToStart -> 36.dp
                        }, label = "tabSwipeIconSize"
                    )

                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(color)
                            .padding(horizontal = 20.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.icons_add_bookmark), // TODO Tab swipe icons
                            contentDescription = "Bookmark icon",
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(iconSize)
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.icons_close_circled), // TODO Tab swipe icons
                            contentDescription = "Delete icon",
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(iconSize)
                        )
                    }
                },
                dismissContent = {
                    TabRow(
                        tab = tab,
                        selected = tab.id == selectedTabId,
                        thumbnailStorage = thumbnailStorage,
                        onSelected = onTabSelected,
                        onDeleted = onTabDeleted
                    )
                },
                modifier = Modifier.animateItemPlacement()
            )
            */
        }
    }
}
