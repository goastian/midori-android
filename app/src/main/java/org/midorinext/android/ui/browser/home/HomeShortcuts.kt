package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.midorinext.android.R
import org.midorinext.android.ext.toCleanHost
import org.midorinext.android.newtab.*
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.HomeShortcut
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.animation.MidoriMotion
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.theme.MidoriDialogSystemBars

@Stable
internal class HomeShortcutUiState {
    var collectionId by mutableStateOf<String?>(null)
    var pickerParentId by mutableStateOf<String?>(null)
    var selectedId by mutableStateOf<String?>(null)
    var editorId by mutableStateOf<String?>(null)
    var editorParentId by mutableStateOf("")
    var showEditor by mutableStateOf(false)
    var folderEditorId by mutableStateOf<String?>(null)
    var showFolderEditor by mutableStateOf(false)
    var movingId by mutableStateOf<String?>(null)
    var removingFolderId by mutableStateOf<String?>(null)
    var failed by mutableStateOf(false)
    var busy by mutableStateOf(false)

    companion object {
        val Saver = mapSaver(
            save = { state: HomeShortcutUiState -> mapOf(
                "collection" to state.collectionId, "picker" to state.pickerParentId,
                "selected" to state.selectedId, "editor" to state.editorId,
                "editorParent" to state.editorParentId, "showEditor" to state.showEditor,
                "folderEditor" to state.folderEditorId, "showFolderEditor" to state.showFolderEditor,
                "moving" to state.movingId, "removing" to state.removingFolderId,
            ) },
            restore = { saved -> HomeShortcutUiState().apply {
                collectionId = saved["collection"] as String?
                pickerParentId = saved["picker"] as String?
                selectedId = saved["selected"] as String?
                editorId = saved["editor"] as String?
                editorParentId = saved["editorParent"] as String
                showEditor = saved["showEditor"] as Boolean
                folderEditorId = saved["folderEditor"] as String?
                showFolderEditor = saved["showFolderEditor"] as Boolean
                movingId = saved["moving"] as String?
                removingFolderId = saved["removing"] as String?
            } },
        )
    }

    fun perform(scope: CoroutineScope, action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                failed = !action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }
}

@Composable
internal fun HomeShortcutGrid(
    sites: List<HomeShortcut>, allSites: List<HomeShortcut>, preferences: AppPreferences,
    columns: Int, palette: HomePalette, wallpaper: Boolean, viewModel: NativeHomeViewModel,
    dataReady: Boolean, state: HomeShortcutUiState, onOpenUrl: (String) -> Unit,
) {
    val showAdd = !preferences.hideHomeAddShortcut
    val visible = sites.take(columns * 2 - if (showAdd) 1 else 0)
    val tiles: List<HomeShortcut?> = visible + if (showAdd) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { site ->
                    key(site?.id ?: "add") {
                        HomeShortcutTile(site, allSites, preferences, palette, viewModel, dataReady,
                            Modifier.weight(1f), onClick = {
                                when {
                                    site == null -> state.pickerParentId = ""
                                    site.isFolder -> state.collectionId = site.id
                                    else -> onOpenUrl(site.url)
                                }
                            }, onLongClick = site?.let { { state.selectedId = it.id } })
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeShortcutTile(
    site: HomeShortcut?, allSites: List<HomeShortcut>, preferences: AppPreferences,
    palette: HomePalette, viewModel: NativeHomeViewModel, dataReady: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)?,
) {
    val size = if (preferences.homeLargeShortcuts) 64.dp else 48.dp
    val shape = homeShortcutIconShape(preferences.homeShortcutShape)
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(MidoriMotion.FeedbackDurationMillis),
        label = "shortcut press",
    )
    Column(modifier.testTag(if (site == null) "home_add_shortcut" else "home_shortcut_tile")
        .combinedClickable(interactionSource = interactions, indication = ripple(),
            onClick = onClick, onLongClick = onLongClick, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }.testTag("home_shortcut_icon"),
            shape = shape, color = palette.tile) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    site == null -> Icon(painterResource(mozilla.components.ui.icons.R.drawable.mozac_ic_plus_24), null,
                        Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    site.isFolder -> {
                        val children = allSites.filter { it.parentId == site.id }.take(4)
                        if (children.isEmpty()) Icon(painterResource(R.drawable.icons_folder_add), null, Modifier.size(26.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        else Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            children.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    row.forEach { HomeFavicon(it.url, viewModel, dataReady, Modifier.size(if (preferences.homeLargeShortcuts) 22.dp else 16.dp)) }
                                }
                            }
                        }
                    }
                    else -> HomeFavicon(site.url, viewModel, dataReady,
                        Modifier.size(if (preferences.homeLargeShortcuts) 40.dp else 28.dp))
                }
            }
        }
        Text(site?.title ?: stringResource(R.string.home_add_shortcut),
            Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(shadow = palette.shadow),
            color = palette.primary, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun HomeShortcutDialogs(
    state: HomeShortcutUiState, sites: List<HomeShortcut>,
    viewModel: NativeHomeViewModel, dataReady: Boolean, onOpenUrl: (String) -> Unit,
) {
    val repository = viewModel.preferencesRepository
    val scope = rememberCoroutineScope()
    val moveLeftOffset = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    val folders = sites.filter { it.isFolder }
    val selected = sites.firstOrNull { it.id == state.selectedId }
    val collection = state.collectionId
    val folder = folders.firstOrNull { it.id == collection }
    if (collection != null && (collection.isEmpty() || folder != null)) {
        ModalBottomSheet(onDismissRequest = { state.collectionId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.testTag("home_shortcuts_sheet")) {
            MidoriDialogSystemBars()
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(folder?.title ?: stringResource(R.string.home_shortcuts), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge)
                    if (folder != null) IconButton(onClick = { state.selectedId = folder.id },
                        modifier = Modifier.testTag("home_folder_actions")) {
                        Icon(painterResource(R.drawable.icons_more_vertical), stringResource(R.string.home_edit_folder))
                    }
                }
                val children = sites.filter { it.parentId == collection }
                val collectionState = key(collection) { rememberLazyListState() }
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 440.dp).testTag("home_collection_list"),
                    state = collectionState, contentPadding = PaddingValues(vertical = 12.dp)) {
                    if (children.isEmpty()) item {
                        Text(stringResource(R.string.home_folder_empty), Modifier.padding(vertical = 24.dp))
                    }
                    items(children, key = { it.id }) { site ->
                        ListItem(headlineContent = { Text(site.title) },
                            supportingContent = { Text(if (site.isFolder)
                                stringResource(R.string.home_folder_contents, sites.count { it.parentId == site.id }) else site.url.toCleanHost()) },
                            leadingContent = {
                                if (site.isFolder) Icon(painterResource(R.drawable.icons_folder_add), null)
                                else HomeFavicon(site.url, viewModel, dataReady, Modifier.size(28.dp))
                            }, trailingContent = {
                                IconButton(onClick = { state.selectedId = site.id }) {
                                    Icon(painterResource(R.drawable.icons_more_vertical),
                                        stringResource(if (site.isFolder) R.string.home_edit_folder else R.string.home_edit_shortcut))
                                }
                            }, modifier = Modifier.testTag("home_collection_item:${site.id}").combinedClickable(onClick = {
                                if (site.isFolder) state.collectionId = site.id else {
                                    state.collectionId = null
                                    onOpenUrl(site.url)
                                }
                            }, onLongClick = { state.selectedId = site.id }))
                    }
                }
                FilledTonalButton(onClick = { state.collectionId = null; state.pickerParentId = collection },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) { Text(stringResource(R.string.home_add_shortcut)) }
            }
        }
    }
    if (selected != null) {
        val siblings = sites.filter { it.parentId == selected.parentId }
        val position = siblings.indexOf(selected)
        AlertDialog(onDismissRequest = { state.selectedId = null }, title = { Text(selected.title) }, text = {
            Column {
                HomeAction(if (selected.isFolder) R.string.home_edit_folder else R.string.home_edit_shortcut) {
                    if (selected.isFolder) { state.folderEditorId = selected.id; state.showFolderEditor = true }
                    else { state.editorId = selected.id; state.editorParentId = selected.parentId; state.showEditor = true }
                    state.selectedId = null
                }
                if (!selected.isFolder) HomeAction(R.string.home_move_to_folder) {
                    state.movingId = selected.id; state.selectedId = null
                }
                HomeAction(R.string.home_move_left, position + moveLeftOffset in siblings.indices && !state.busy) {
                    state.perform(scope) { repository.moveHomeShortcut(selected.id, moveLeftOffset); state.selectedId = null; true }
                }
                HomeAction(R.string.home_move_right, position - moveLeftOffset in siblings.indices && !state.busy) {
                    state.perform(scope) { repository.moveHomeShortcut(selected.id, -moveLeftOffset); state.selectedId = null; true }
                }
                HomeAction(if (selected.isFolder) R.string.home_remove_folder else R.string.adblock_remove, !state.busy) {
                    if (selected.isFolder) { state.removingFolderId = selected.id; state.selectedId = null }
                    else state.perform(scope) { repository.deleteHomeShortcut(selected.id); state.selectedId = null; true }
                }
            }
        }, confirmButton = { TextButton(onClick = { state.selectedId = null }) { Text(stringResource(R.string.cancel)) } })
    }
    state.removingFolderId?.let { id ->
        AlertDialog(onDismissRequest = { state.removingFolderId = null },
            title = { Text(stringResource(R.string.home_remove_folder)) },
            text = { Text(stringResource(R.string.home_remove_folder_message)) }, confirmButton = {
                TextButton(enabled = !state.busy, onClick = {
                    state.perform(scope) {
                        repository.deleteHomeShortcut(id)
                        if (state.collectionId == id) state.collectionId = null
                        state.removingFolderId = null
                        true
                    }
                }) { Text(stringResource(R.string.adblock_remove)) }
            }, dismissButton = { TextButton(onClick = { state.removingFolderId = null }) { Text(stringResource(R.string.cancel)) } })
    }
    state.movingId?.let { id ->
        AlertDialog(onDismissRequest = { state.movingId = null }, title = { Text(stringResource(R.string.home_move_to_folder)) }, text = {
            LazyColumn {
                items(listOf<String?>(null) + folders.map { it.id }) { target ->
                    val title = folders.firstOrNull { it.id == target }?.title ?: stringResource(R.string.home_folder_root)
                    TextButton(enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("home_move_target:${target.orEmpty()}"),
                        onClick = { state.perform(scope) {
                            repository.moveHomeShortcutToFolder(id, target.orEmpty()).also { if (it) state.movingId = null }
                        } }) { Text(title) }
                }
            }
        }, confirmButton = { TextButton(onClick = { state.movingId = null }) { Text(stringResource(R.string.cancel)) } })
    }
    if (state.showFolderEditor) {
        val editing = folders.firstOrNull { it.id == state.folderEditorId }
        var title by rememberSaveable(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
        var attempted by rememberSaveable(editing?.id) { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { state.showFolderEditor = false },
            title = { Text(stringResource(if (editing == null) R.string.home_add_folder else R.string.home_edit_folder)) }, text = {
                OutlinedTextField(title, { title = it }, singleLine = true,
                    label = { Text(stringResource(R.string.bookmarks_folder_title_placeholder)) },
                    isError = attempted && title.isBlank(), supportingText = {
                        if (attempted && title.isBlank()) Text(stringResource(R.string.home_folder_name_required))
                    }, modifier = Modifier.testTag("home_folder_name"))
            }, confirmButton = {
                TextButton(enabled = !state.busy, modifier = Modifier.testTag("home_folder_save"), onClick = {
                    attempted = true
                    if (title.isNotBlank()) state.perform(scope) {
                        repository.saveHomeFolder(editing?.id, title).also { if (it) state.showFolderEditor = false }
                    }
                }) { Text(stringResource(R.string.bookmark_save)) }
            }, dismissButton = { TextButton(onClick = { state.showFolderEditor = false }) { Text(stringResource(R.string.cancel)) } })
    }
    if (state.showEditor) {
        HomeShortcutEditor(sites.firstOrNull { it.id == state.editorId }, state.busy,
            onDismiss = { state.showEditor = false }) { title, url ->
            state.perform(scope) {
                repository.saveHomeShortcut(state.editorId, title, url, state.editorParentId)
                    .also { if (it) state.showEditor = false }
            }
        }
    }
    state.pickerParentId?.let { parentId ->
        HomeSitePicker(parentId, state, sites, viewModel, dataReady)
    }
    if (state.failed) AlertDialog(onDismissRequest = { state.failed = false },
        text = { Text(stringResource(R.string.home_shortcut_save_failed)) },
        confirmButton = { TextButton(onClick = { state.failed = false }) { Text(stringResource(R.string.ok)) } })
}

@Composable
private fun HomeAction(label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label), Modifier.fillMaxWidth())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeSitePicker(parentId: String, state: HomeShortcutUiState, sites: List<HomeShortcut>,
    viewModel: NativeHomeViewModel, dataReady: Boolean) {
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val category = homeSiteCategories.firstOrNull { it.id == categoryId }
    val categoryTitle = category?.let { stringResource(it.title) }
    val recent by viewModel.recentVisits.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarks by produceState(initialValue = emptyList<HomePage>(), viewModel) {
        try { value = viewModel.savedBookmarks() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
    }
    val existingUrls = sites.filter { !it.isFolder && it.parentId == parentId }.map { it.url }.toSet()
    ModalBottomSheet(onDismissRequest = { state.pickerParentId = null },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), modifier = Modifier.testTag("home_site_picker")) {
        MidoriDialogSystemBars()
        BoxWithConstraints(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            val columns = ((maxWidth - 32.dp) / 80.dp).toInt().coerceIn(2, 6)
            val listState = key(parentId, categoryId) { rememberLazyListState() }
            LazyColumn(Modifier.fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (category != null) IconButton(onClick = { categoryId = null }) {
                            Icon(painterResource(R.drawable.icons_arrow_backward), stringResource(R.string.home_site_categories),
                                modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f })
                        }
                        Text(categoryTitle ?: stringResource(R.string.home_add_shortcut), style = MaterialTheme.typography.titleLarge)
                    }
                }
                item {
                    FilledTonalButton(modifier = Modifier.fillMaxWidth().testTag("home_add_website"), onClick = {
                        state.editorId = null; state.editorParentId = parentId; state.pickerParentId = null; state.showEditor = true
                    }) {
                        Icon(painterResource(R.drawable.icons_add_tab), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.home_add_website))
                    }
                }
                if (category == null) {
                    item { Text(stringResource(R.string.home_site_categories), style = MaterialTheme.typography.titleSmall) }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(homeSiteCategories, key = { it.id }) { option ->
                                Surface(onClick = { categoryId = option.id }, color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.width(112.dp).testTag("home_category:${option.id}")) {
                                    Column(Modifier.heightIn(min = 92.dp).padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(painterResource(R.drawable.icons_folder), null, Modifier.size(24.dp))
                                        Text(stringResource(option.title), style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    item { Text(stringResource(R.string.home_suggested_sites), style = MaterialTheme.typography.titleSmall) }
                } else if (parentId.isEmpty()) {
                    item {
                        OutlinedButton(enabled = !state.busy, onClick = {
                            state.perform(scope) {
                                viewModel.preferencesRepository.addHomeShortcutFolder(categoryTitle!!, category.sites.map { it.title to it.url })
                                    .also { if (it) state.pickerParentId = null }
                            }
                        }, modifier = Modifier.fillMaxWidth().testTag("home_add_category_folder")) {
                            Icon(painterResource(R.drawable.icons_folder_add), null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.home_create_category_folder))
                        }
                    }
                }
                homeSuggestedSiteGrid(category?.sites ?: homeSuggestedSites, columns, "catalog", existingUrls,
                    state.busy, viewModel, dataReady) { site ->
                    state.perform(scope) { viewModel.preferencesRepository.saveHomeShortcut(null, site.title, site.url, parentId) }
                }
                if (category == null) {
                    if (recent.isNotEmpty()) {
                        item { Text(stringResource(R.string.home_recent_visits), style = MaterialTheme.typography.titleSmall) }
                        homeSuggestedSiteGrid(recent.take(8).map { HomeSuggestedSite(it.title, it.url) }, columns,
                            "recent", existingUrls, state.busy, viewModel, dataReady) { site ->
                            state.perform(scope) { viewModel.preferencesRepository.saveHomeShortcut(null, site.title, site.url, parentId) }
                        }
                    }
                    if (bookmarks.isNotEmpty()) {
                        item { Text(stringResource(R.string.home_saved_bookmarks), style = MaterialTheme.typography.titleSmall) }
                        homeSuggestedSiteGrid(bookmarks.map { HomeSuggestedSite(it.title, it.url) }, columns,
                            "bookmark", existingUrls, state.busy, viewModel, dataReady) { site ->
                            state.perform(scope) { viewModel.preferencesRepository.saveHomeShortcut(null, site.title, site.url, parentId) }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.homeSuggestedSiteGrid(
    sites: List<HomeSuggestedSite>, columns: Int, keyPrefix: String, existingUrls: Set<String>,
    busy: Boolean, viewModel: NativeHomeViewModel, dataReady: Boolean, onAdd: (HomeSuggestedSite) -> Unit,
) {
    items(sites.chunked(columns), key = { "$keyPrefix:${it.first().url}" }) { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { site ->
                key(site.url) {
                    HomeSuggestedSiteTile(site, site.url in existingUrls, busy, viewModel, dataReady,
                        modifier = Modifier.weight(1f), onAdd = { onAdd(site) })
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun HomeSuggestedSiteTile(site: HomeSuggestedSite, added: Boolean, busy: Boolean,
    viewModel: NativeHomeViewModel, dataReady: Boolean, modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val action = stringResource(if (added) R.string.home_site_added else R.string.home_add_shortcut)
    Column(modifier.clip(RoundedCornerShape(12.dp))
        .clickable(enabled = !added && !busy, role = Role.Button, onClickLabel = action, onClick = onAdd)
        .testTag("home_suggested_site:${site.url}").padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(56.dp)) {
            Surface(Modifier.size(48.dp).align(Alignment.Center), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(contentAlignment = Alignment.Center) {
                    HomeFavicon(site.url, viewModel, dataReady, Modifier.size(32.dp))
                }
            }
            Surface(Modifier.size(20.dp).align(Alignment.BottomEnd), shape = CircleShape,
                color = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (added) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer) {
                Icon(painterResource(if (added) R.drawable.icons_check else R.drawable.icons_add_tab),
                    action, Modifier.padding(3.dp))
            }
        }
        Text(site.title, modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp), maxLines = 2,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
    }
}
