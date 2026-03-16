/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.midorinext.android.R

data class MenuAction(
    val iconRes: Int,
    val titleRes: Int,
    val onClick: () -> Unit,
)

data class MenuToggleAction(
    val iconRes: Int,
    val titleRes: Int,
    val isChecked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuBottomSheet(
    onDismiss: () -> Unit,
    hasSession: Boolean,
    canGoForward: Boolean,
    isDesktopMode: Boolean,
    isPinningSupported: Boolean,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onShare: () -> Unit,
    onBookmark: () -> Unit,
    onDesktopModeChanged: (Boolean) -> Unit,
    onAddToHomescreen: () -> Unit,
    onFindInPage: () -> Unit,
    onBookmarks: () -> Unit,
    onCollections: () -> Unit,
    onHistory: () -> Unit,
    onDownloads: () -> Unit,
    onAddons: () -> Unit,
    onPasswordGenerator: () -> Unit,
    onSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            // Session-specific actions
            if (hasSession) {
                // Navigation row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    QuickActionButton(
                        iconRes = mozilla.components.ui.icons.R.drawable.mozac_ic_forward_24,
                        label = stringResource(R.string.menu_forward),
                        enabled = canGoForward,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onForward()
                            onDismiss()
                        },
                    )
                    QuickActionButton(
                        iconRes = R.drawable.ic_refresh,
                        label = stringResource(R.string.menu_refresh),
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRefresh()
                            onDismiss()
                        },
                    )
                    QuickActionButton(
                        iconRes = R.drawable.ic_share,
                        label = stringResource(R.string.menu_share),
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onShare()
                            onDismiss()
                        },
                    )
                    QuickActionButton(
                        iconRes = R.drawable.ic_bookmark_border,
                        label = stringResource(R.string.bookmark_add_page),
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onBookmark()
                            onDismiss()
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Desktop mode toggle
                MenuToggleItem(
                    iconRes = R.drawable.ic_desktop,
                    title = stringResource(R.string.menu_request_desktop_site),
                    isChecked = isDesktopMode,
                    onCheckedChange = { checked ->
                        onDesktopModeChanged(checked)
                    },
                )

                // Add to homescreen
                if (isPinningSupported) {
                    MenuItem(
                        iconRes = R.drawable.ic_add_to_homescreen,
                        title = stringResource(R.string.menu_add_to_homescreen),
                        onClick = {
                            onAddToHomescreen()
                            onDismiss()
                        },
                    )
                }

                // Find in page
                MenuItem(
                    iconRes = R.drawable.ic_find_in_page,
                    title = stringResource(R.string.menu_find_in_page),
                    onClick = {
                        onFindInPage()
                        onDismiss()
                    },
                )

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // General menu items
            MenuItem(
                iconRes = R.drawable.ic_bookmarks,
                title = stringResource(R.string.bookmarks_title),
                onClick = {
                    onBookmarks()
                    onDismiss()
                },
            )
            MenuItem(
                iconRes = R.drawable.ic_collections,
                title = stringResource(R.string.collections_title),
                onClick = {
                    onCollections()
                    onDismiss()
                },
            )
            MenuItem(
                iconRes = R.drawable.ic_history,
                title = stringResource(R.string.history_title),
                onClick = {
                    onHistory()
                    onDismiss()
                },
            )
            MenuItem(
                iconRes = R.drawable.ic_downloads,
                title = stringResource(R.string.downloads_title),
                onClick = {
                    onDownloads()
                    onDismiss()
                },
            )
            MenuItem(
                iconRes = R.drawable.ic_extension,
                title = stringResource(R.string.menu_addons),
                onClick = {
                    onAddons()
                    onDismiss()
                },
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))

            MenuItem(
                iconRes = R.drawable.ic_password_generate,
                title = stringResource(R.string.passwords_generate),
                onClick = {
                    onPasswordGenerator()
                    onDismiss()
                },
            )
            MenuItem(
                iconRes = R.drawable.ic_settings,
                title = stringResource(R.string.settings),
                onClick = {
                    onSettings()
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
        )
    }
}

@Composable
private fun MenuItem(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun MenuToggleItem(
    iconRes: Int,
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}
