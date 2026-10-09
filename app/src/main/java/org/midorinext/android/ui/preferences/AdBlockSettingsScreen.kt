package org.midorinext.android.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import org.midorinext.android.R
import org.midorinext.android.adblock.AdBlockSettings
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.adblock.DesktopHostFilter
import org.midorinext.android.adblock.OfficialFilter
import org.midorinext.android.adblock.OfficialFilterCatalog
import org.midorinext.android.adblock.hostOf
import org.midorinext.android.ui.preferences.widgets.PreferenceGroupLabel
import org.midorinext.android.ui.preferences.widgets.PreferenceRow
import org.midorinext.android.ui.preferences.widgets.PreferenceToggle
import javax.inject.Inject

@HiltViewModel
class AdBlockSettingsViewModel @Inject constructor(
    val settings: AdBlockSettings,
    val filter: DesktopHostFilter,
) : ViewModel()

@Composable
fun AdBlockSettingsScreen(
    onSites: () -> Unit,
    onTrackerSources: () -> Unit,
    onAdSources: () -> Unit,
    viewModel: AdBlockSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.settings.state.collectAsStateWithLifecycle()
    PreferenceScreenScaffold(title = stringResource(R.string.adblock_settings_title)) {
        PreferenceGroupLabel(label = R.string.adblock_default_level)
        BlockingLevel.entries.forEach { level ->
            BlockingLevelRow(level, config.level) { viewModel.settings.setLevel(level) }
        }
        PreferenceToggle(
            label = R.string.adblock_cookie_banners,
            description = R.string.adblock_cookie_banners_description,
            value = config.rejectCookieBanners,
            onValueChange = viewModel.settings::setRejectCookieBanners,
        )
        PreferenceGroupLabel(label = R.string.adblock_exceptions)
        PreferenceRow(label = R.string.adblock_manage_sites, onClicked = onSites)
        PreferenceGroupLabel(label = R.string.adblock_sources)
        PreferenceRow(label = R.string.adblock_tracker_sources, onClicked = onTrackerSources)
        PreferenceRow(label = R.string.adblock_ad_sources, onClicked = onAdSources)
        PreferenceToggle(
            label = R.string.adblock_strict,
            description = R.string.adblock_strict_description,
            value = config.strict,
            onValueChange = viewModel.settings::setStrict,
        )
        Text(
            text = stringResource(R.string.adblock_scope_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
internal fun BlockingLevelRow(level: BlockingLevel, selected: BlockingLevel, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = level == selected, onClick = onClick)
        Text(stringResource(level.label()), style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun BlockingLevel.label(): Int = when (this) {
    BlockingLevel.OFF -> R.string.adblock_level_off
    BlockingLevel.TRACKERS -> R.string.adblock_level_trackers
    BlockingLevel.TRACKERS_AND_ADS -> R.string.adblock_level_trackers_ads
}

@Composable
fun AdBlockSitesScreen(viewModel: AdBlockSettingsViewModel = hiltViewModel()) {
    val config by viewModel.settings.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var site by remember { mutableStateOf("") }
    var editingHost by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(BlockingLevel.OFF) }
    var invalid by remember { mutableStateOf(false) }
    PreferenceScreenScaffold(title = stringResource(R.string.adblock_manage_sites)) {
        PreferenceRow(label = R.string.adblock_add_site, onClicked = {
            site = ""
            editingHost = null
            selected = BlockingLevel.OFF
            showAdd = true
        })
        if (config.siteLevels.isEmpty()) {
            Text(stringResource(R.string.adblock_no_sites), modifier = Modifier.padding(16.dp))
        }
        config.siteLevels.toSortedMap().forEach { (host, level) ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    site = host
                    editingHost = host
                    selected = level
                    showAdd = true
                }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(host)
                    Text(stringResource(level.label()), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { viewModel.settings.setSiteLevel(host, null) }) {
                    Text(stringResource(R.string.adblock_remove))
                }
            }
        }
    }
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(if (editingHost == null) R.string.adblock_add_site else R.string.adblock_manage_sites)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = site,
                        onValueChange = { site = it; invalid = false },
                        label = { Text(stringResource(R.string.adblock_site_domain)) },
                        enabled = editingHost == null,
                        isError = invalid,
                        singleLine = true,
                    )
                    if (invalid) Text(stringResource(R.string.adblock_invalid_site), color = MaterialTheme.colorScheme.error)
                    BlockingLevel.entries.forEach { level ->
                        BlockingLevelRow(level, selected) { selected = level }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val host = hostOf("https://${site.trim()}")
                    if (host == null || site.contains('/') || site.contains(':')) invalid = true
                    else {
                        viewModel.settings.setSiteLevel(host, selected)
                        showAdd = false
                        site = ""
                    }
                }) { Text(stringResource(R.string.adblock_add)) }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.adblock_cancel)) } },
        )
    }
}

@Composable
fun AdBlockSourcesScreen(
    tracker: Boolean,
    viewModel: AdBlockSettingsViewModel = hiltViewModel(),
) {
    val config by viewModel.settings.state.collectAsStateWithLifecycle()
    val failedSources by viewModel.filter.failedSources.collectAsStateWithLifecycle()
    val ruleCounts by viewModel.filter.ruleCounts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    PreferenceScreenScaffold(title = stringResource(if (tracker) R.string.adblock_tracker_sources else R.string.adblock_ad_sources)) {
        PreferenceRow(label = R.string.adblock_add_source, onClicked = { showAdd = true })
        PreferenceRow(label = R.string.adblock_update_all_sources, onClicked = viewModel.filter::refreshAll)
        if (tracker) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.adblock_gecko_source))
                    Text(stringResource(R.string.adblock_gecko_source_description), style = MaterialTheme.typography.bodySmall)
                }
                Checkbox(
                    checked = config.builtInTrackers,
                    onCheckedChange = viewModel.settings::setBuiltInTrackers,
                )
            }
        }
        PreferenceGroupLabel(label = R.string.adblock_core_sources)
        OfficialFilterCatalog.all.filter { it.tracker == tracker && !it.regional }.forEach { source ->
            OfficialSourceRow(source, source.id in config.officialEnabled,
                config.officialUpdatedAt[source.id] ?: 0L, source.id in failedSources,
                ruleCounts[source.id], viewModel)
        }
        if (!tracker) {
            PreferenceGroupLabel(label = R.string.adblock_regional_sources)
            OfficialFilterCatalog.all.filter { it.regional }.forEach { source ->
                OfficialSourceRow(source, source.id in config.officialEnabled,
                    config.officialUpdatedAt[source.id] ?: 0L, source.id in failedSources,
                    ruleCounts[source.id], viewModel)
            }
        }
        if (config.sources.any { it.tracker == tracker }) PreferenceGroupLabel(label = R.string.adblock_custom_sources)
        config.sources.filter { it.tracker == tracker }.forEach { source ->
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.url, style = MaterialTheme.typography.bodyMedium)
                    Text(sourceStatus(source.enabled, source.url in failedSources, source.updatedAt),
                        style = MaterialTheme.typography.bodySmall)
                    ruleCounts[source.url]?.let { count ->
                        Text(stringResource(R.string.adblock_source_rules, count), style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        TextButton(onClick = { viewModel.filter.refreshSource(source.url) }) { Text(stringResource(R.string.adblock_refresh)) }
                        TextButton(onClick = { viewModel.filter.removeSource(source.url) }) { Text(stringResource(R.string.adblock_remove)) }
                    }
                }
                Switch(checked = source.enabled, onCheckedChange = { viewModel.settings.setSourceEnabled(source.url, it) })
            }
        }
    }
    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text(stringResource(R.string.adblock_add_source)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; invalid = false },
                    label = { Text(stringResource(R.string.adblock_source_url)) },
                    singleLine = true,
                    isError = invalid,
                )
                if (invalid) Text(stringResource(R.string.adblock_invalid_source), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.adblock_source_format), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = {
            if (viewModel.settings.addSource(url, tracker)) {
                showAdd = false
                url = ""
            } else invalid = true
        }) { Text(stringResource(R.string.adblock_add)) } },
        dismissButton = { TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.adblock_cancel)) } },
    )
}

@Composable
private fun OfficialSourceRow(
    source: OfficialFilter,
    enabled: Boolean,
    updatedAt: Long,
    failed: Boolean,
    ruleCount: Int?,
    viewModel: AdBlockSettingsViewModel,
) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(source.title, style = MaterialTheme.typography.bodyMedium)
            Text(sourceStatus(enabled, failed, updatedAt), style = MaterialTheme.typography.bodySmall)
            ruleCount?.let { count ->
                Text(stringResource(R.string.adblock_source_rules, count), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { viewModel.filter.refreshOfficial(source.id) }) {
                Text(stringResource(R.string.adblock_refresh))
            }
        }
        Checkbox(checked = enabled, onCheckedChange = { viewModel.settings.setOfficialEnabled(source.id, it) })
    }
}

@Composable
private fun sourceStatus(enabled: Boolean, failed: Boolean, updatedAt: Long): String = when {
    !enabled -> stringResource(R.string.adblock_source_disabled)
    failed -> stringResource(R.string.adblock_source_failed)
    updatedAt > 0 -> stringResource(R.string.adblock_source_updated, java.text.DateFormat.getDateTimeInstance().format(updatedAt))
    else -> stringResource(R.string.adblock_source_pending)
}
