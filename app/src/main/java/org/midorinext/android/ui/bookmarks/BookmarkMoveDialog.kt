package org.midorinext.android.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import org.midorinext.android.R
import org.midorinext.android.storage.bookmarks.BookmarkFolderChoice
import org.midorinext.android.ui.widgets.YesNoDialog

@Composable
fun BookmarkMoveDialog(
    item: BookmarkNode,
    viewModel: BookmarksScreenViewModel,
    onDismiss: () -> Unit = {},
    onSubmit: (toGuid: String?) -> Unit = {}
) {
    var selectedFolderGuid by remember(item.guid) { mutableStateOf(item.parentGuid) }
    var path by remember(item.guid) { mutableStateOf(emptyList<BookmarkFolderChoice>()) }
    LaunchedEffect(item.guid) { path = viewModel.destinationAncestors(item.parentGuid) }

    YesNoDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.bookmarks_move_x_to, stringResource(
            if (item.type == BookmarkNodeType.ITEM) R.string.bookmarks_bookmark else R.string.bookmarks_folder
        )),
        onYes = { onSubmit(selectedFolderGuid); onDismiss() },
        onNo = onDismiss,
        yesText = stringResource(R.string.save)
    ) {
        val parent = path.lastOrNull()
        if (parent != null) {
            val folders = remember(parent.guid) { viewModel.destinationFolders(parent.guid) }.collectAsLazyPagingItems()
            val retainedFolders = folders.itemCount
            SideEffect { org.midorinext.android.diagnostics.ScenarioMetrics.rows(
                org.midorinext.android.diagnostics.ScenarioMetrics.Rows.FOLDER_PICKER, retainedFolders
            ) }
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(count = path.size, key = { "ancestor-${path[it].guid}" }) { index ->
                    val folder = path[index]
                    FolderDestinationRow(folder, selectedFolderGuid,
                        onSelected = { selectedFolderGuid = folder.guid },
                        onExpand = { path = path.take(index + 1) })
                }
                items(count = folders.itemCount, key = folders.itemKey { it.guid }) { index ->
                    val folder = folders[index] ?: return@items
                    if (folder.guid != item.guid) {
                        FolderDestinationRow(folder, selectedFolderGuid,
                            onSelected = { selectedFolderGuid = folder.guid },
                            onExpand = { path = path + folder })
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderDestinationRow(
    folder: BookmarkFolderChoice,
    selected: String?,
    onSelected: () -> Unit,
    onExpand: () -> Unit
) {
    val isSelected = selected == folder.guid
    CompositionLocalProvider(LocalContentColor provides if (isSelected) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(48.dp).fillMaxWidth()
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable(onClick = onSelected)
        ) {
            Icon(painterResource(R.drawable.icons_folder), "Folder icon", Modifier.padding(horizontal = 12.dp))
            Text(folder.title.orEmpty(), Modifier.weight(1f))
            if (folder.hasChildren) {
                IconButton(onClick = onExpand) {
                    Icon(painterResource(R.drawable.icons_chevron_forward), "Arrow")
                }
            }
        }
    }
}
