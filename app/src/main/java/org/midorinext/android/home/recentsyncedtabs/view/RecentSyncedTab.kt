/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.recentsyncedtabs.view

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.images.ImageLoadRequest
import mozilla.components.concept.sync.DeviceType
import mozilla.components.support.ktx.kotlin.trimmed
import org.midorinext.android.R
import org.midorinext.android.compose.ContextualMenu
import org.midorinext.android.compose.MenuItem
import org.midorinext.android.compose.Image
import org.midorinext.android.compose.ThumbnailCard
import org.midorinext.android.compose.button.Button
import org.midorinext.android.home.recentsyncedtabs.RecentSyncedTab
import org.midorinext.android.home.recenttabs.RecentTab
import org.midorinext.android.theme.MidoriTheme
import org.midorinext.android.theme.Theme

private const val THUMBNAIL_SIZE = 108

/**
 * A recent synced tab card.
 *
 * @param tab The [RecentSyncedTab] to display.
 * @param storage [ThumbnailStorage] to obtain tab thumbnail bitmaps from.
 * @param backgroundColor The background [Color] of the item.
 * @param buttonBackgroundColor The background [Color] of the item's button.
 * @param buttonTextColor The [Color] of the button's text.
 * @param onRecentSyncedTabClick Invoked when the user clicks on the recent synced tab.
 * @param onSeeAllSyncedTabsButtonClick Invoked when user clicks on the "See all" button in the synced tab card.
 * @param onRemoveSyncedTab Invoked when user clicks on the "Remove" dropdown menu option.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
fun RecentSyncedTab(
    tab: RecentSyncedTab?,
    storage: ThumbnailStorage,
    backgroundColor: Color = MidoriTheme.colors.layer2,
    buttonBackgroundColor: Color = MidoriTheme.colors.actionSecondary,
    buttonTextColor: Color = MidoriTheme.colors.textActionSecondary,
    onRecentSyncedTabClick: (RecentSyncedTab) -> Unit,
    onSeeAllSyncedTabsButtonClick: () -> Unit,
    onRemoveSyncedTab: (RecentSyncedTab) -> Unit,
) {
    var isDropdownExpanded by remember { mutableStateOf(false) }

    fun removeSyncedTab(recentSyncedTab: RecentSyncedTab) {
        isDropdownExpanded = false
        onRemoveSyncedTab(recentSyncedTab)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { tab?.let { onRecentSyncedTabClick(tab) } },
                onLongClick = { isDropdownExpanded = true },
            ),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = backgroundColor,
        elevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                if (tab == null) {
                    RecentTabImagePlaceholder()
                } else {
                    val imageModifier = Modifier
                        .size(108.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))

                    if (tab.previewImageUrl != null) {
                        Image(
                            url = tab.previewImageUrl,
                            contentScale = ContentScale.Crop,
                            modifier = imageModifier,
                        )
                    } else {
                        ThumbnailCard(
                            url = tab.url,
                            request = ImageLoadRequest(
                                id = tab.url.hashCode().toString(),
                                size = LocalDensity.current.run { THUMBNAIL_SIZE.dp.toPx().toInt() },
                                isPrivate = false,
                            ),
                            storage = storage,
                            modifier = imageModifier,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    if (tab == null) {
                        RecentTabTitlePlaceholder()
                    } else {
                        Text(
                            text = tab.title.trimmed(),
                            color = MidoriTheme.colors.textPrimary,
                            fontSize = 14.sp,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2,
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (tab == null) {
                            Box(
                                modifier = Modifier
                                    .background(MidoriTheme.colors.layer3)
                                    .size(18.dp),
                            )
                        } else {
                            Image(
                                painter = painterResource(R.drawable.ic_synced_tabs),
                                contentDescription = stringResource(
                                    R.string.recent_tabs_synced_device_icon_content_description,
                                ),
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (tab == null) {
                            TextLinePlaceHolder()
                        } else {
                            Text(
                                text = tab.deviceDisplayName,
                                color = MidoriTheme.colors.textSecondary,
                                fontSize = 12.sp,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                text = if (tab != null) {
                    stringResource(R.string.recent_tabs_see_all_synced_tabs_button_text)
                } else {
                    ""
                },
                textColor = MidoriTheme.colors.textActionSecondary,
                backgroundColor = if (tab == null) {
                    MidoriTheme.colors.layer3
                } else {
                    MidoriTheme.colors.actionSecondary
                },
                tint = MidoriTheme.colors.iconActionSecondary,
                onClick = onSeeAllSyncedTabsButtonClick,
            )
        }
    }

    ContextualMenu(
        showMenu = isDropdownExpanded && tab != null,
        onDismissRequest = { isDropdownExpanded = false },
        menuItems = listOf(
            MenuItem(stringResource(id = R.string.recent_synced_tab_menu_item_remove)) {
                tab?.let { removeSyncedTab(it) }
            },
        ),
    )
}

/**
 * A placeholder for a recent tab image.
 */
@Composable
private fun RecentTabImagePlaceholder() {
    Box(
        modifier = Modifier
            .size(108.dp, 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color = MidoriTheme.colors.layer3),
    )
}

/**
 * A placeholder for a tab title.
 */
@Composable
private fun RecentTabTitlePlaceholder() {
    Column {
        TextLinePlaceHolder()

        Spacer(modifier = Modifier.height(8.dp))

        TextLinePlaceHolder()
    }
}

/**
 * A placeholder for a single line of text.
 */
@Composable
private fun TextLinePlaceHolder() {
    Box(
        modifier = Modifier
            .height(12.dp)
            .fillMaxWidth()
            .background(MidoriTheme.colors.layer3),
    )
}

@Preview
@Composable
private fun LoadedRecentSyncedTab() {
    val tab = RecentSyncedTab(
        deviceDisplayName = "Midori on MacBook",
        deviceType = DeviceType.DESKTOP,
        title = "This is a long site title",
        url = "https://astian.org",
        previewImageUrl = "https://astian.org",
    )
    MidoriTheme(theme = Theme.getTheme()) {
        RecentSyncedTab(
            tab = tab,
            storage = ThumbnailStorage(LocalContext.current),
            onRecentSyncedTabClick = {},
            onSeeAllSyncedTabsButtonClick = {},
            onRemoveSyncedTab = {},
        )
    }
}

@Preview
@Composable
private fun LoadingRecentSyncedTab() {
    MidoriTheme(theme = Theme.getTheme()) {
        RecentSyncedTab(
            tab = null,
            storage = ThumbnailStorage(LocalContext.current),
            buttonBackgroundColor = MidoriTheme.colors.layer3,
            onRecentSyncedTabClick = {},
            onSeeAllSyncedTabsButtonClick = {},
            onRemoveSyncedTab = {},
        )
    }
}
