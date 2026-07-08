package org.midorinext.android.ui.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mozilla.components.feature.addons.ui.translateDescription
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.feature.addons.ui.translateSummary
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.ScreenHeader
import java.util.Locale

@Composable
fun AddonDetailScreen(
    addonId: String,
    onClose: () -> Unit,
    viewModel: ExtensionViewModel
) {
    val addons by viewModel.addons.collectAsState()
    val addon = remember(addons, addonId) { addons.find { it.id == addonId } }
    val installingAddonId by viewModel.installingAddonId.collectAsState()
    val webExtensionStates by viewModel.webExtensionStates.collectAsState()
    val error by viewModel.error.collectAsState()
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.extensions_detail_title),
            scrollableState = scrollState
        )

        if (addon == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val context = LocalContext.current
        val name = addon.translateName(context)
        val summary = stripHtml(addon.translateSummary(context))
        val description = stripHtml(addon.translateDescription(context))
        val isInstalled = addon.isInstalled()
        val isInstalling = installingAddonId == addon.id
        var showUninstallDialog by remember { mutableStateOf(false) }
        val browserAction = remember(webExtensionStates, addon.id) {
            webExtensionStates[addon.id]?.browserAction
        }
        val errorText = error?.let { stringResource(it.messageRes) }

        if (errorText != null) {
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(text = "OK")
                    }
                }
            ) {
                Text(text = errorText)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Header with icon and name
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddonIcon(
                    iconUrl = addon.iconUrl,
                    modifier = Modifier.size(64.dp),
                    cornerRadius = 12.dp,
                    fallbackIconSize = 40.dp
                )

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val authorName = addon.author?.name
                    if (!authorName.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.extensions_author_by, authorName),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Rating and stats
            val rating = addon.rating
            if (rating != null) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RatingStars(rating = rating.average)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", rating.average),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.extensions_ratings, rating.reviews.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (addon.downloadUrl.isNotBlank()) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .height(48.dp)
                                    .width(1.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = addon.version,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.extensions_version, ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Summary
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Full description
            if (description.isNotBlank() && description != summary) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Permissions section
            val permissions = addon.translatePermissions(context)
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.extensions_permissions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            if (permissions.isEmpty()) {
                Text(
                    text = stringResource(R.string.extensions_no_permissions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                permissions.forEach { permission ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("•", modifier = Modifier.padding(end = 8.dp))
                        Text(
                            text = permission,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Enabled toggle for installed addons
            if (isInstalled) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.extensions_enabled),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = addon.isEnabled(),
                        onCheckedChange = {
                            viewModel.toggleEnabled(addon.id, addon.isEnabled())
                        }
                    )
                }

                if (browserAction != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { viewModel.triggerBrowserAction(addon.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.extensions_open_popup))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // Bottom action button
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isInstalled) {
                // Uninstall button
                OutlinedButton(
                    onClick = { showUninstallDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.extensions_uninstall))
                }
            } else {
                // Install button
                Button(
                    onClick = { viewModel.installAddon(addon) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !isInstalling
                ) {
                    if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.extensions_installing))
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.icons_extension),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.extensions_install))
                    }
                }
            }
        }

        // Uninstall confirmation dialog
        if (showUninstallDialog) {
            AlertDialog(
                onDismissRequest = { showUninstallDialog = false },
                title = { Text(stringResource(R.string.extensions_uninstall)) },
                text = { Text(stringResource(R.string.extensions_uninstall_confirm, name)) },
                confirmButton = {
                    TextButton(onClick = {
                        showUninstallDialog = false
                        viewModel.uninstall(addon.id)
                        onClose()
                    }) {
                        Text(stringResource(R.string.extensions_uninstall))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUninstallDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}
