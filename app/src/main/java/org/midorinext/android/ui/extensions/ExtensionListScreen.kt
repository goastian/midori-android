package org.midorinext.android.ui.extensions

import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.ui.translateName
import mozilla.components.feature.addons.ui.translateSummary
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun ExtensionListScreen(
    onClose: () -> Unit,
    onAddonClick: (String) -> Unit = {},
    viewModel: ExtensionViewModel = hiltViewModel()
) {
    val addons by viewModel.addons.collectAsState()
    val addonsLoading by viewModel.addonsLoading.collectAsState()
    val addonsError by viewModel.addonsError.collectAsState()
    val error by viewModel.error.collectAsState()
    val installingAddonId by viewModel.installingAddonId.collectAsState()
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredAddons = remember(addons, searchQuery, context) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            addons
        } else {
            addons.filter { addon ->
                addon.translateName(context).contains(query, ignoreCase = true) ||
                    stripHtml(addon.translateSummary(context)).contains(query, ignoreCase = true)
            }
        }
    }
    val installedAddons = remember(filteredAddons) { filteredAddons.filter { it.isInstalled() } }
    val recommendedAddons = remember(filteredAddons) { filteredAddons.filter { !it.isInstalled() } }
    val errorText = error?.let { stringResource(it.messageRes) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(
            title = stringResource(R.string.extensions_title),
            scrollableState = lazyListState
        )

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

        if (addons.isNotEmpty() || searchQuery.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.extensions_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                addonsLoading && addons.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.extensions_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                addonsError != null && addons.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.icons_extension),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.extensions_load_error),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadAddons(allowCache = false) }) {
                            Text(stringResource(R.string.extensions_retry))
                        }
                    }
                }
                else -> {
                    LazyColumn(state = lazyListState) {
                        if (filteredAddons.isEmpty() && searchQuery.isNotBlank()) {
                            item(key = "search_empty") {
                                Text(
                                    text = stringResource(R.string.extensions_search_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp, vertical = 32.dp)
                                )
                            }
                        }

                        // Installed section
                        if (installedAddons.isNotEmpty()) {
                            item(key = "header_installed") {
                                SectionHeader(
                                    title = stringResource(R.string.extensions_installed),
                                    count = installedAddons.size
                                )
                            }
                            items(installedAddons, key = { "installed_${it.id}" }) { addon ->
                                AddonRow(
                                    addon = addon,
                                    isInstalling = installingAddonId == addon.id,
                                    onClick = { onAddonClick(addon.id) }
                                )
                            }
                        }

                        // Recommended section
                        if (recommendedAddons.isNotEmpty()) {
                            item(key = "header_recommended") {
                                SectionHeader(
                                    title = stringResource(R.string.extensions_recommended),
                                    count = recommendedAddons.size
                                )
                            }
                            items(recommendedAddons, key = { "recommended_${it.id}" }) { addon ->
                                AddonRow(
                                    addon = addon,
                                    isInstalling = installingAddonId == addon.id,
                                    onClick = { onAddonClick(addon.id) }
                                )
                            }
                        }

                        // More Extensions button
                        item {
                            MoreExtensionsButton()
                        }

                        // Bottom spacing
                        item { Spacer(Modifier.height(16.dp)) }
                    }

                    // Pull-to-refresh indicator at top when refreshing with existing data
                    if (addonsLoading && addons.isNotEmpty()) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AddonRow(
    addon: Addon,
    isInstalling: Boolean,
    onClick: () -> Unit
) {
    val name = addon.translateName(LocalContext.current)
    val summary = stripHtml(addon.translateSummary(LocalContext.current))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = !isInstalling) { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Addon icon
            AddonIcon(
                iconUrl = addon.iconUrl,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                cornerRadius = 8.dp,
                fallbackIconSize = 32.dp
            )

            Spacer(Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Rating
                val rating = addon.rating
                if (rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingStars(rating = rating.average)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "(${rating.reviews})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Status badge
            if (isInstalling) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else if (addon.isInstalled()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.extensions_installed_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RatingStars(rating: Float, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        val fullStars = rating.toInt()
        val hasHalf = (rating - fullStars) >= 0.5f
        repeat(5) { index ->
            val tint = when {
                index < fullStars -> MaterialTheme.colorScheme.primary
                index == fullStars && hasHalf -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            Text(
                text = "★",
                color = tint,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Strip HTML tags from a string, converting entities and tags to plain text. */
internal fun stripHtml(html: String): String {
    if (html.isBlank()) return html
    return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
        .toString()
        .trim()
}

@Composable
private fun MoreExtensionsButton() {
    val context = LocalContext.current

    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://addons.mozilla.org/en-US/android/"))
            context.startActivity(intent)
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(stringResource(R.string.extensions_more))
    }
}
