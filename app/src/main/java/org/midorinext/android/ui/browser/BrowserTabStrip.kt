package org.midorinext.android.ui.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon
import mozilla.components.browser.state.state.TabSessionState
import org.midorinext.android.R

@Composable
fun BrowserTabStrip(
    tabs: List<TabSessionState>,
    selectedTabId: String?,
    browserIcons: BrowserIcons,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    onNewTab: () -> Unit,
) {
    val listState = rememberLazyListState()
    val tabIds = tabs.map { it.id }
    LaunchedEffect(selectedTabId, tabIds) {
        val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(tabs, key = { it.id }) { tab ->
                val isSelected = tab.id == selectedTabId
                Row(
                    modifier = Modifier
                        .width(188.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.secondaryContainer
                        )
                        .clickable(role = Role.Tab) { onTabSelected(tab.id) }
                        .semantics { selected = isSelected }
                        .padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        if (tab.content.private && tab.content.url.isBlank()) {
                            Image(
                                painter = painterResource(R.drawable.icons_privacy_mask),
                                contentDescription = null,
                            )
                        } else {
                            tab.content.icon?.let { icon ->
                                Image(bitmap = icon.asImageBitmap(), contentDescription = null)
                            } ?: browserIcons.Loader(url = tab.content.url, isPrivate = tab.content.private) {
                                WithIcon { icon ->
                                    Image(painter = icon.painter, contentDescription = null)
                                }
                                Placeholder {
                                    Image(
                                        painter = painterResource(R.drawable.icons_internet),
                                        contentDescription = null,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = tab.content.title.ifBlank {
                            stringResource(
                                if (tab.content.private) R.string.browser_new_tab_private
                                else R.string.browser_new_tab
                            )
                        },
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tab_tray_close_tab)) {
                                onTabClosed(tab.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.icons_close),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.browser_new_tab), onClick = onNewTab),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.icons_add_tab),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
