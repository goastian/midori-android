package org.midorinext.android.ui.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.R
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.ui.browser.home.HomePrivateBrowsingContent
import org.midorinext.android.ui.theme.LocalMidoriTheme

@Composable
fun TabRow(
    tab: TabSessionState,
    selected: Boolean,
    thumbnailStorage: ThumbnailStorage,
    onSelected: (tab: TabSessionState) -> Unit,
    onDeleted: (tab: TabSessionState) -> Unit,
    contentBlockerState: ContentBlockerState,
    modifier: Modifier = Modifier
) {
    val isTabBlocked = contentBlockerState.getStatusForTab(tab.id) != ContentBlockerState.Status.ALLOWED
    val isPrivateBrowsingHome = LocalMidoriTheme.current.private && tab.content.url == ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
            .clickable { onSelected(tab) }
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .width(90.dp)
                .height(70.dp)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
        ) {
            if (isPrivateBrowsingHome) {
                Image(
                    painter = painterResource(R.drawable.icons_privacy_mask),
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    contentDescription = "privacy_mask",
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            } else {
                TabThumbnail(tab.id, 90.dp, thumbnailStorage, contentBlockerState)
            }
        }

        Column(modifier = Modifier
            .weight(2f)
            .padding(start = 12.dp)) {
            Text(
                text = if (isTabBlocked) stringResource(id = R.string.blocked_website)
                        else if (isPrivateBrowsingHome) stringResource(id = R.string.browser_new_tab_private)
                        else tab.content.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 20.sp
            )
            Text(
                tab.content.url.toCleanHost(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = { onDeleted(tab) }) {
            Icon(painter = painterResource(id = R.drawable.icons_close), contentDescription = "Delete")
        }
    }
}