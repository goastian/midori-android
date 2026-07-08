package org.midorinext.android.ui.bookmarks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.ui.widgets.YesNoDialog
import org.midorinext.android.R
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType

@Composable
fun BookmarkMoveDialog(
    item: BookmarkNode,
    folderTree: BookmarkNode,
    onDismiss: () -> Unit = {},
    onSubmit: (toGuid: String?) -> Unit = {}
) {
    var selectedFolderGuid by remember { mutableStateOf(item.parentGuid) }

    YesNoDialog(
        onDismissRequest = { onDismiss() },
        title = stringResource(id = R.string.bookmarks_move_x_to, stringResource(
            if (item.type == BookmarkNodeType.ITEM) R.string.bookmarks_bookmark
            else R.string.bookmarks_folder
        )),
        onYes = {
            onSubmit(selectedFolderGuid)
            onDismiss()
        },
        onNo = { onDismiss() },
        yesText = stringResource(id = R.string.save)
    ) {
        BookmarkFolderTreeItem(
            currentFolder = folderTree,
            excludeGuid = if (item.type == BookmarkNodeType.FOLDER) item.guid else null,
            selectedFolderGuid = selectedFolderGuid,
            onSelected = { selectedFolderGuid = it }
        )
    }
}

@Composable
fun BookmarkFolderTreeItem(
    currentFolder: BookmarkNode,
    excludeGuid: String?,
    selectedFolderGuid: String?,
    onSelected: (String?) -> Unit
) {
    val background = if (selectedFolderGuid == currentFolder.guid) MaterialTheme.colorScheme.primary else Color.Transparent

    var open by remember { mutableStateOf( true) }

    val arrowRotation by animateFloatAsState(targetValue = if (open) 90f else 180f, label = "arrowRotation")

    Column(modifier = Modifier.padding(start = 16.dp)) {
        val children = currentFolder.children
            ?.filter { it.guid != excludeGuid }
            ?.sortedBy { it.title?.lowercase() }

        CompositionLocalProvider(LocalContentColor provides
            if (selectedFolderGuid == currentFolder.guid) MaterialTheme.colorScheme.onPrimary else LocalContentColor.current
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(48.dp)
                    .fillMaxWidth()
                    .background(background)
                    .clickable { onSelected(currentFolder.guid) }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.icons_folder),
                    contentDescription = "Folder icon",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Text(text = currentFolder.title ?: "", modifier = Modifier.weight(2f))

                if (children?.isNotEmpty() == true) {
                    IconButton(onClick = { open = !open }) {
                        Icon(
                            painterResource(
                                id = R.drawable.icons_chevron_forward
                            ),
                            contentDescription = "Arrow",
                            modifier = Modifier.rotate(arrowRotation)
                        )
                    }
                }
            }
        }
        AnimatedVisibility(open) {
            Column {
                children?.forEach {
                    BookmarkFolderTreeItem(it, excludeGuid, selectedFolderGuid, onSelected)
                }
            }
        }
    }
}