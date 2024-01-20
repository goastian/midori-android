/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.bookmarks.selectfolder

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.dimensionResource
import mozilla.components.concept.storage.BookmarkNode
import org.midorinext.android.R
import org.midorinext.android.library.LibrarySiteItem
import org.midorinext.android.library.bookmarks.BookmarkNodeWithDepth
import org.midorinext.android.library.bookmarks.flatNodeList
import org.midorinext.android.theme.MidoriTheme

class SelectBookmarkFolderComposeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AbstractComposeView(context, attrs, defStyleAttr) {

    var folderList by mutableStateOf<List<BookmarkNodeWithDepth>>(emptyList())
    var selectedFolderGuid by mutableStateOf<String?>(null)
    var onFolderClick: ((BookmarkNode) -> Unit)? = null

    fun updateFolderList(tree: BookmarkNode?, hideFolderGuid: String?) {
        folderList = tree
            ?.flatNodeList(hideFolderGuid)
            ?.drop(1)
            .orEmpty()
    }

    @Composable
    override fun Content() {
        val indent = dimensionResource(R.dimen.bookmark_select_folder_indent)

        MidoriTheme {
            LazyColumn {
                items(
                    items = folderList,
                    key = { folder -> folder.node.guid }
                ) { folder ->
                    LibrarySiteItem(
                        modifier = Modifier.padding(start = indent * minOf(MAX_DEPTH, folder.depth)),
                        favicon = R.drawable.ic_folder_icon,
                        title = folder.node.title,
                        selected = folder.node.guid == selectedFolderGuid,
                        item = folder.node,
                        onItemClick = onFolderClick
                    )
                }
            }
        }
    }

    companion object {
        private const val MAX_DEPTH = 10
    }

}
