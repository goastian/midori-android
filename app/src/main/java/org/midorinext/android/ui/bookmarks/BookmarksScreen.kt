package org.midorinext.android.ui.bookmarks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.ScreenHeader

@Composable
fun BookmarksScreen(
    onBrowse: () -> Unit,
    viewModel: BookmarksScreenViewModel = hiltViewModel()
) {
    val lazyListState = rememberLazyListState()
    val isRootFolder by viewModel.isRootFolder.collectAsState()
    val folder by viewModel.folder.collectAsState()

    BackHandler(!isRootFolder) {
        folder.parentGuid?.let { viewModel.visitFolder(it) }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = folder.title ?: stringResource(id = R.string.bookmarks),
            scrollableState = lazyListState,
            actions = { NewFolderAction(viewModel) }
        )
        BookmarksList(viewModel = viewModel, lazyListState = lazyListState, onBrowse = onBrowse)
    }
}

@Composable
fun NewFolderAction(viewModel: BookmarksScreenViewModel) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showCreateFolderDialog = true }) {
        Icon(painter = painterResource(id = R.drawable.icons_folder_add), contentDescription = "Add folder") // TODO change icon
    }
    if (showCreateFolderDialog) {
        BookmarkEditDialog(
            onSubmit = { title, _ ->
                viewModel.addFolder(title, viewModel.folderGuid)
            },
            onDismiss = { showCreateFolderDialog = false }
        )
    }
}

