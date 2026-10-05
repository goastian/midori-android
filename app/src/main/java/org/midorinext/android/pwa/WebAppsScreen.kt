package org.midorinext.android.pwa

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.ScreenHeader
import java.net.URI

@Composable
fun WebAppsScreen(viewModel: WebAppsViewModel = hiltViewModel()) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val installingUrl by viewModel.installingUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var suggestion by remember { mutableStateOf<WebAppSuggestion?>(null) }
    var removing by remember { mutableStateOf<InstalledWebApp?>(null) }
    var clearing by remember { mutableStateOf<InstalledWebApp?>(null) }
    val installFailed = stringResource(R.string.pwa_install_failed)

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column {
            ScreenHeader(title = stringResource(R.string.pwa_manage_title))
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.pwa_installed_tab, R.string.pwa_discover_tab).forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index },
                        text = { Text(stringResource(title)) })
                }
            }
            if (tab == 0) {
                if (apps.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.pwa_empty), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(148.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(apps, key = { it.startUrl }) { app ->
                            InstalledCard(app, viewModel,
                                onOpen = {
                                    context.startActivity(Intent(context, WebAppActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        data = Uri.parse(app.startUrl)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                                    })
                                },
                                onRemove = { removing = app },
                                onClear = { clearing = app })
                        }
                    }
                }
            } else {
                val installed = remember(apps) { apps.mapTo(hashSetOf()) { it.startUrl } }
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(WebAppCatalog.suggestions, key = { it.url }) { item ->
                        Card(onClick = { suggestion = item },
                            modifier = Modifier.fillMaxWidth().height(136.dp)) {
                            Column(Modifier.fillMaxSize().padding(8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                WebAppIcon(item.url, item.name, viewModel, Modifier.size(52.dp))
                                Text(item.name, modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (item.url in installed) {
                                    Text(stringResource(R.string.pwa_installed_badge),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    suggestion?.let { item ->
        val installed = apps.any { it.startUrl == item.url }
        AlertDialog(
            onDismissRequest = { if (installingUrl == null) suggestion = null },
            icon = { WebAppIcon(item.url, item.name, viewModel, Modifier.size(64.dp)) },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(URI(item.url).host ?: item.url)
                    Text(stringResource(R.string.pwa_install_explanation))
                }
            },
            confirmButton = {
                if (installed) {
                    TextButton(onClick = { tab = 0; suggestion = null }) {
                        Text(stringResource(R.string.pwa_installed_tab))
                    }
                } else {
                    Button(onClick = {
                        viewModel.install(item) { success ->
                            if (success) tab = 0
                            else Toast.makeText(context, installFailed, Toast.LENGTH_SHORT).show()
                            suggestion = null
                        }
                    }, enabled = installingUrl == null) {
                        if (installingUrl == item.url) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text(stringResource(R.string.pwa_install_action))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { suggestion = null }, enabled = installingUrl == null) {
                    Text(stringResource(R.string.pwa_cancel))
                }
            })
    }

    removing?.let { app ->
        AlertDialog(onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.pwa_remove_title, app.name)) },
            text = { Text(stringResource(R.string.pwa_remove_explanation)) },
            confirmButton = {
                TextButton(onClick = { viewModel.uninstall(app); removing = null }) {
                    Text(stringResource(R.string.pwa_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text(stringResource(R.string.pwa_cancel)) }
            })
    }
    clearing?.let { app ->
        val failed = stringResource(R.string.pwa_clear_data_failed)
        val done = stringResource(R.string.pwa_clear_data_done)
        AlertDialog(onDismissRequest = { clearing = null },
            title = { Text(stringResource(R.string.pwa_clear_data_title, app.name)) },
            text = { Text(stringResource(R.string.pwa_clear_data_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearSiteData(app) { success ->
                        Toast.makeText(context, if (success) done else failed, Toast.LENGTH_SHORT).show()
                    }
                    clearing = null
                }) { Text(stringResource(R.string.pwa_clear_data)) }
            },
            dismissButton = {
                TextButton(onClick = { clearing = null }) { Text(stringResource(R.string.pwa_cancel)) }
            })
    }
}

@Composable
private fun InstalledCard(app: InstalledWebApp, viewModel: WebAppsViewModel,
    onOpen: () -> Unit, onRemove: () -> Unit, onClear: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WebAppIcon(app.startUrl, app.name, viewModel, Modifier.size(42.dp))
                Text(app.name, modifier = Modifier.weight(1f), maxLines = 2,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            }
            Text(URI(app.startUrl).host ?: app.startUrl, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pwa_enabled), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall)
                val description = stringResource(R.string.pwa_enable_app, app.name)
                Switch(checked = app.enabled, onCheckedChange = { viewModel.setEnabled(app, it) },
                    modifier = Modifier.semantics { contentDescription = description })
            }
            Row {
                TextButton(onClick = onOpen, enabled = app.enabled) {
                    Text(stringResource(R.string.pwa_open))
                }
                TextButton(onClick = onRemove) { Text(stringResource(R.string.pwa_remove)) }
            }
            TextButton(onClick = onClear) { Text(stringResource(R.string.pwa_clear_data)) }
        }
    }
}

@Composable
private fun WebAppIcon(url: String, name: String, viewModel: WebAppsViewModel, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, url) {
        value = viewModel.loadIcon(url)
    }
    val shape = RoundedCornerShape(12.dp)
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = modifier.clip(shape))
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium)
        }
    }
}
