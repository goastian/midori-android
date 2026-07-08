package org.midorinext.android.ui.preferences.permissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import org.midorinext.android.R
import org.midorinext.android.ui.preferences.PreferencesViewModel
import org.midorinext.android.ui.widgets.ScreenHeader
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage

@Composable
fun PermissionsSettingsList(
    viewModel: PreferencesViewModel
) {
    LaunchedEffect(viewModel) {
        viewModel.ensurePermissionsLoaded()
    }

    val permissions = viewModel.permissions.collectAsLazyPagingItems()
    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader(title = stringResource(id = R.string.permissions_settings_label))
            if (lazyListState.canScrollBackward) {
                HorizontalDivider()
            }
        }
        LazyColumn(
            modifier = Modifier.padding(top = 56.dp),
            state = lazyListState
        ) {
            item {
                TextButton(onClick = { viewModel.revokeAllPermissions() }) {
                    Text("Revoke all permissions")
                }
            }
            items(permissions.itemCount) { index ->
                permissions[index]?.let {
                    PermissionsOriginRow(viewModel, it)
                }
            }
            permissions.apply {
                when {
                    loadState.refresh is LoadState.Loading || loadState.append is LoadState.Loading -> {
                        item {
                            CircularProgressIndicator()
                        }
                    }
                    loadState.refresh is LoadState.Error -> {
                        val error = permissions.loadState.refresh as LoadState.Error
                        Log.e("MIDORI_Permissions", "Error refreshing permissions list: ${error.error.message}")
                        error.error.localizedMessage?.let {
                            item { Text(it) }
                        }
                    }
                    loadState.append is LoadState.Error -> {
                        val error = permissions.loadState.append as LoadState.Error
                        Log.e("MIDORI_Permissions", "Error appending permissions list: ${error.error.message}")
                        error.error.localizedMessage?.let {
                            item { Text(it) }
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun PermissionsOriginRow(
    viewModel: PreferencesViewModel,
    sitePermissions: SitePermissions,
) {
    var showPermissions by remember { mutableStateOf(false) }

    Column {
        Row (verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable { showPermissions = !showPermissions }
        ) {
            Text(sitePermissions.origin)
        }
        if (showPermissions) {
            PermissionsGrantedRows(viewModel, sitePermissions)
        }
    }
}

@Composable
fun PermissionsGrantedRows(
    viewModel: PreferencesViewModel,
    sitePermissions: SitePermissions
) {
    SitePermissionsStorage.Permission.entries
        // .filter { sitePermissions[it] != SitePermissions.Status.NO_DECISION }
        .forEach { permission ->
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp)
                    .height(48.dp)
            ) {
                var showPermissionEditDropdown by remember { mutableStateOf(false) }

                Text(
                    text = when (permission) {
                        SitePermissionsStorage.Permission.MICROPHONE -> "Microphone" // TODO translate
                        SitePermissionsStorage.Permission.BLUETOOTH -> "Bluetooth" // TODO translate
                        SitePermissionsStorage.Permission.CAMERA -> "Camera" // TODO translate
                        SitePermissionsStorage.Permission.LOCAL_STORAGE -> "Local storage" // TODO translate
                        SitePermissionsStorage.Permission.NOTIFICATION -> "Notification" // TODO translate
                        SitePermissionsStorage.Permission.LOCATION -> "Location" // TODO translate
                        SitePermissionsStorage.Permission.AUTOPLAY_AUDIBLE -> "Autoplay (audible)" // TODO translate
                        SitePermissionsStorage.Permission.AUTOPLAY_INAUDIBLE -> "Autoplay (inaudible)" // TODO translate
                        SitePermissionsStorage.Permission.MEDIA_KEY_SYSTEM_ACCESS -> "Media key system access" // TODO translate
                        SitePermissionsStorage.Permission.STORAGE_ACCESS -> "Cross origin storage access" // TODO translate
                        SitePermissionsStorage.Permission.LOCAL_DEVICE_ACCESS -> "Local device access" // TODO translate
                        SitePermissionsStorage.Permission.LOCAL_NETWORK_ACCESS -> "Local network access" // TODO translate
                    },
                    modifier = Modifier.weight(2f)
                )
                Box {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp ),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable { showPermissionEditDropdown = !showPermissionEditDropdown }
                    ) {
                        Text(text = when (sitePermissions[permission]) {
                            SitePermissions.Status.BLOCKED -> "Blocked" // TODO translate
                            SitePermissions.Status.NO_DECISION -> "No decision" // TODO translate
                            SitePermissions.Status.ALLOWED -> "Allowed" // TODO translate
                        })
                        Icon(painter = painterResource(R.drawable.icons_edit), contentDescription = "Selected preference")
                    }
                    DropdownMenu(
                        expanded = showPermissionEditDropdown,
                        onDismissRequest = { showPermissionEditDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = "Allow") },
                            onClick = {
                                viewModel.updatePermissions(sitePermissions, permission, SitePermissions.Status.ALLOWED)
                                showPermissionEditDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(text = "Block") },
                            onClick = {
                                viewModel.updatePermissions(sitePermissions, permission, SitePermissions.Status.BLOCKED)
                                showPermissionEditDropdown = false
                            }
                        )
                    }
                }
            }
        }
}