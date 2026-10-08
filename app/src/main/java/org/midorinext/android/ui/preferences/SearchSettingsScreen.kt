package org.midorinext.android.ui.preferences

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.midorinext.android.BuildConfig
import org.midorinext.android.R
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.preferences.app.SearchSetting
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.preferences.widgets.PreferenceGroupLabel
import org.midorinext.android.ui.preferences.widgets.PreferenceRow
import org.midorinext.android.usecases.SearchEngineOption
import org.midorinext.android.usecases.SearchEngines
import org.midorinext.android.widget.WidgetProvider
import coil.compose.AsyncImage

@Composable
fun SearchSettingsScreen(
    navigateTo: (NavDestination) -> Unit,
    viewModel: PreferencesViewModel = hiltViewModel(),
) {
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    PreferenceScreenScaffold(title = stringResource(R.string.settings_search_title)) {
        PreferenceGroupLabel(R.string.settings_search_engines_group)
        SettingsNavRow(
            label = R.string.settings_default_search_engine,
            description = selectedEngineName(prefs),
            onClicked = { navigateTo(NavDestination.DefaultSearchEngineSettings) },
        )
        SettingsNavRow(
            label = R.string.settings_manage_search_engines,
            description = stringResource(R.string.settings_manage_search_engines_summary),
            onClicked = { navigateTo(NavDestination.AlternativeSearchEngineSettings) },
        )
        SearchWidgetRow()

        PreferenceGroupLabel(R.string.settings_search_suggestions_group)
        SearchSwitchRow(
            R.string.settings_show_search_suggestions,
            !prefs.disableSearchSuggestions,
        ) { viewModel.updateSearchSetting(SearchSetting.SUGGESTIONS, it) }
        SearchCheckboxRow(
            R.string.settings_search_suggestions_private,
            prefs.searchSuggestionsInPrivate,
            enabled = !prefs.disableSearchSuggestions,
        ) { viewModel.updateSearchSetting(SearchSetting.PRIVATE_SUGGESTIONS, it) }
        SearchCheckboxRow(
            R.string.settings_trending_suggestions,
            !prefs.disableTrendingSuggestions,
            enabled = !prefs.disableSearchSuggestions,
        ) { viewModel.updateSearchSetting(SearchSetting.TRENDING, it) }
        SearchSwitchRow(
            R.string.settings_recent_searches,
            !prefs.disableRecentSearches,
        ) { viewModel.updateSearchSetting(SearchSetting.RECENT, it) }

        if (BuildConfig.FLAVOR_version == "original") {
            PreferenceGroupLabel(R.string.settings_address_bar_suggest_group)
            SearchSwitchRow(R.string.settings_search_history, !prefs.disableHistorySuggestions) {
                viewModel.updateSearchSetting(SearchSetting.HISTORY, it)
            }
            SearchSwitchRow(R.string.settings_search_bookmarks, !prefs.disableBookmarkSuggestions) {
                viewModel.updateSearchSetting(SearchSetting.BOOKMARKS, it)
            }
            SearchSwitchRow(R.string.settings_search_open_tabs, !prefs.disableTabSuggestions) {
                viewModel.updateSearchSetting(SearchSetting.TABS, it)
            }
        }

        PreferenceGroupLabel(R.string.settings_address_bar_preferences_group)
        SearchSwitchRow(R.string.settings_clipboard_suggestions, !prefs.disableClipboardSuggestions) {
            viewModel.updateSearchSetting(SearchSetting.CLIPBOARD, it)
        }
        SearchSwitchRow(R.string.settings_voice_search, !prefs.disableVoiceSearch) {
            viewModel.updateSearchSetting(SearchSetting.VOICE, it)
        }
        SearchSwitchRow(R.string.settings_autocomplete_urls, !prefs.disableUrlAutocomplete) {
            viewModel.updateSearchSetting(SearchSetting.URL_AUTOCOMPLETE, it)
        }
    }
}

@Composable
fun DefaultSearchEngineSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    var addEngine by remember { mutableStateOf(false) }
    PreferenceScreenScaffold(title = stringResource(R.string.settings_default_search_engine)) {
        PreferenceGroupLabel(R.string.settings_normal_search_engine)
        SearchEngines.options(prefs).forEach { option ->
            SearchEngineRadioRow(
                option = option,
                selected = SearchEngines.selectedId(prefs) == option.id,
            ) {
                if (option.builtIn != null) viewModel.updateSearchEngine(option.builtIn)
                else viewModel.updateCustomSearchEngine(option.id)
            }
        }

        PreferenceGroupLabel(R.string.settings_private_search_engine)
        SearchRadioRow(
            label = stringResource(R.string.settings_same_search_engine),
            selected = !prefs.useSeparatePrivateSearchEngine,
        ) { viewModel.updateSeparatePrivateSearchEngine(false) }
        SearchEngines.options(prefs).forEach { option ->
            SearchEngineRadioRow(
                option = option,
                selected = prefs.useSeparatePrivateSearchEngine &&
                    SearchEngines.selectedId(prefs, private = true) == option.id,
            ) {
                if (option.builtIn != null) viewModel.updatePrivateSearchEngine(option.builtIn)
                else viewModel.updateCustomSearchEngine(option.id, private = true)
            }
        }

        PreferenceRow(
            label = R.string.settings_add_search_engine,
            trailing = {
                Icon(painterResource(R.drawable.icons_add_tab), contentDescription = null)
            },
            onClicked = { addEngine = true },
        )
    }
    if (addEngine) {
        AddSearchEngineDialog(
            onDismiss = { addEngine = false },
            onAdd = { name, url, suggestions ->
                viewModel.addCustomSearchEngine(name, url, suggestions)
                addEngine = false
            },
        )
    }
}

@Composable
fun AlternativeSearchEngineSettingsScreen(viewModel: PreferencesViewModel = hiltViewModel()) {
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    var engineToRemove by remember { mutableStateOf<SearchEngineOption?>(null) }
    PreferenceScreenScaffold(title = stringResource(R.string.settings_manage_search_engines)) {
        PreferenceGroupLabel(R.string.settings_alternative_search_engines)
        SearchEngines.options(prefs).forEach { option ->
            val isDefault = option.id == SearchEngines.selectedId(prefs) ||
                (prefs.useSeparatePrivateSearchEngine && option.id == SearchEngines.selectedId(prefs, true))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).clickable(enabled = !isDefault) {
                        viewModel.updateAlternativeSearchEngine(
                            option.id, option.id in prefs.hiddenSearchEnginesList,
                        )
                    }.padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isDefault || option.id !in prefs.hiddenSearchEnginesList,
                        enabled = !isDefault,
                        onCheckedChange = { viewModel.updateAlternativeSearchEngine(option.id, it) },
                    )
                    Text(engineName(option), modifier = Modifier.padding(start = 16.dp))
                }
                if (option.builtIn == null && !isDefault) {
                    IconButton(onClick = { engineToRemove = option }) {
                        Icon(painterResource(R.drawable.icons_trash), contentDescription = stringResource(R.string.delete))
                    }
                }
            }
        }
    }
    engineToRemove?.let { option ->
        AlertDialog(
            onDismissRequest = { engineToRemove = null },
            title = { Text(stringResource(R.string.settings_remove_search_engine)) },
            text = { Text(option.name) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCustomSearchEngine(option.id)
                    engineToRemove = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { engineToRemove = null }) {
                    Text(stringResource(R.string.download_removal_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchSwitchRow(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    PreferenceRow(
        label = label,
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        onClicked = { onCheckedChange(!checked) },
    )
}

@Composable
private fun SearchCheckboxRow(
    label: Int,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) {
            onCheckedChange(!checked)
        }.padding(start = 32.dp, end = 16.dp),
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SearchEngineRadioRow(option: SearchEngineOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        SearchEngineIcon(option, Modifier.padding(start = 24.dp).size(28.dp))
        Text(engineName(option), modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun SearchEngineIcon(option: SearchEngineOption, modifier: Modifier = Modifier) {
    val iconUrl = when (option.builtIn) {
        SearchEnginePreference.ASTIANGO -> "https://astiango.com/favicon.ico"
        SearchEnginePreference.ECOSIA -> "https://www.ecosia.org/favicon.ico"
        SearchEnginePreference.QWANT -> "https://www.qwant.com/favicon.ico"
        SearchEnginePreference.STARTPAGE -> "https://www.startpage.com/favicon.ico"
        SearchEnginePreference.BING -> "https://www.bing.com/favicon.ico"
        SearchEnginePreference.GOOGLE -> "https://www.google.com/favicon.ico"
        SearchEnginePreference.DUCKDUCKGO -> "https://duckduckgo.com/favicon.ico"
        else -> null
    }
    if (iconUrl != null) {
        AsyncImage(
            model = iconUrl,
            contentDescription = null,
            error = painterResource(R.drawable.icons_search),
            modifier = modifier,
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(option.name.take(1), maxLines = 1, overflow = TextOverflow.Clip) }
    }
}

@Composable
private fun SearchRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 24.dp))
    }
}

@Composable
private fun engineName(option: SearchEngineOption): String =
    option.builtIn?.let { searchEngineName(it) } ?: option.name

@Composable
internal fun selectedEngineName(prefs: AppPreferences): String =
    SearchEngines.options(prefs).find { it.id == SearchEngines.selectedId(prefs) }
        ?.let { engineName(it) } ?: searchEngineName(SearchEnginePreference.ASTIANGO)

@Composable
private fun AddSearchEngineDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var suggestionUrl by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && SearchEngines.isValidTemplate(url.trim()) &&
        (suggestionUrl.isBlank() || SearchEngines.isValidTemplate(suggestionUrl.trim()))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_add_search_engine)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_search_engine_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.settings_search_engine_url)) },
                    placeholder = { Text("https://example.com/search?q=%s") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = suggestionUrl, onValueChange = { suggestionUrl = it },
                    label = { Text(stringResource(R.string.settings_search_suggestion_url)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.settings_search_engine_url_hint))
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onAdd(name.trim(), url.trim(), suggestionUrl.trim()) }) {
                Text(stringResource(R.string.settings_add_search_engine))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.download_removal_cancel)) }
        },
    )
}

@Composable
private fun SearchWidgetRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember(context) { AppWidgetManager.getInstance(context) }
    val component = remember(context) { ComponentName(context, WidgetProvider::class.java) }
    var installed by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, manager, component) {
        val refresh = { installed = manager.getAppWidgetIds(component).isNotEmpty() }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val onClick = {
        if (installed || Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            !manager.isRequestPinAppWidgetSupported
        ) {
            showHelp = true
        } else {
            manager.requestPinAppWidget(component, null, null)
        }
        Unit
    }
    PreferenceRow(
        label = R.string.settings_home_screen_widget,
        trailing = { Switch(checked = installed, onCheckedChange = { onClick() }) },
        onClicked = onClick,
    )
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.settings_home_screen_widget)) },
            text = { Text(stringResource(R.string.settings_widget_instructions)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text(stringResource(android.R.string.ok)) }
            },
        )
    }
}
