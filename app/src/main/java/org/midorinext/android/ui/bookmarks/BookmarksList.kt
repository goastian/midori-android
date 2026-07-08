package org.midorinext.android.ui.bookmarks

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipDescription.MIMETYPE_TEXT_URILIST
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.ui.browser.suggest.WebsiteRow
import org.midorinext.android.ui.browser.suggest.WebsiteRowWithIcon
import org.midorinext.android.ui.widgets.Dropdown
import org.midorinext.android.ui.widgets.DropdownItem
import org.midorinext.android.ui.widgets.EmptyPagePlaceholder
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import org.mozilla.reference.browser.storage.BookmarkItemV2
import java.util.Locale
import mozilla.components.feature.contextmenu.R as mozacR
import androidx.core.net.toUri

@Composable
fun BookmarksList(
    viewModel: BookmarksScreenViewModel,
    onBrowse: () -> Unit,
    lazyListState: LazyListState = rememberLazyListState()
) {
    val bookmarksUnordered by viewModel.bookmarks.collectAsState()
    val bookmarks by remember(bookmarksUnordered) { derivedStateOf {
        bookmarksUnordered
            .sortedWith(compareByDescending<BookmarkNode> { it.type }
                .thenBy { it.title?.lowercase(Locale.getDefault()) })
    }}

    val folder by viewModel.folder.collectAsState()

    var editItem: BookmarkNode? by remember { mutableStateOf(null) }
    var moveItem: BookmarkNode? by remember { mutableStateOf(null) }

    if (bookmarks.isNotEmpty()) {
        LazyColumn(state = lazyListState) {
            bookmarks.forEach { bookmark ->
                val itemMenu: @Composable RowScope.() -> Unit = {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.icons_more_vertical),
                                contentDescription = "more"
                            )
                        }
                        Dropdown(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            val folderOrBookmarkString = when (bookmark.type) {
                                BookmarkNodeType.ITEM -> stringResource(R.string.bookmarks_bookmark)
                                BookmarkNodeType.FOLDER -> stringResource(R.string.bookmarks_folder)
                                else -> ""
                            }
                            if (bookmark.type == BookmarkNodeType.ITEM) {
                                DropdownItem(
                                    text = stringResource(mozacR.string.mozac_feature_contextmenu_open_link_in_new_tab),
                                    icon = R.drawable.icons_add_tab,
                                    onClick = {
                                        viewModel.openBookmarkTab(bookmark)
                                        onBrowse()
                                    }
                                )
                                DropdownItem(
                                    text = stringResource(mozacR.string.mozac_feature_contextmenu_open_link_in_private_tab),
                                    icon = R.drawable.icons_privacy_mask,
                                    onClick = {
                                        viewModel.openBookmarkTab(bookmark, private = true)
                                        onBrowse()
                                    }
                                )
                                bookmark.url?.let {
                                    val clipboard = LocalClipboard.current.nativeClipboard
                                    DropdownItem(
                                        text = stringResource(mozacR.string.mozac_feature_contextmenu_copy_link),
                                        icon = R.drawable.icons_paste,
                                        onClick = {
                                            clipboard.setPrimaryClip(ClipData.newRawUri(it, it.toUri()))
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                            DropdownItem(
                                text = stringResource(R.string.bookmarks_move_x_to, folderOrBookmarkString),
                                icon = R.drawable.icons_arrow_forward,
                                onClick = {
                                    moveItem = bookmark
                                    showMenu = false
                                }
                            )
                            DropdownItem(
                                text = "${stringResource(R.string.edit)} $folderOrBookmarkString",
                                icon = R.drawable.icons_edit,
                                onClick = {
                                    editItem = bookmark
                                    showMenu = false
                                }
                            )
                            DropdownItem(
                                text = "${stringResource(R.string.delete)} $folderOrBookmarkString",
                                icon = R.drawable.icons_trash,
                                onClick = {
                                    viewModel.deleteBookmark(bookmark)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                item { // TODO add key and animateItemPlacement
                    when (bookmark.type) {
                        BookmarkNodeType.ITEM -> WebsiteRowWithIcon(
                            title = bookmark.title,
                            url = bookmark.url ?: "",
                            browserIcons = viewModel.browserIcons,
                            trailing = itemMenu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.openBookmarkTab(bookmark)
                                    onBrowse()
                                }
                                .padding(start = 16.dp)
                        )
                        BookmarkNodeType.FOLDER -> WebsiteRow(
                            title = bookmark.title,
                            leading = { Icon(
                                painter = painterResource(id = R.drawable.icons_folder),
                                contentDescription = "folder icon",
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(4.dp)
                            ) },
                            trailing = itemMenu,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.visitFolder(bookmark.guid) }
                                .padding(start = 16.dp)
                        )
                        BookmarkNodeType.SEPARATOR -> HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    } else {
        EmptyPagePlaceholder(
            icon = R.drawable.icons_bookmark,
            title = stringResource(id = R.string.bookmarks_empty_title,
                folder.title ?: stringResource(R.string.bookmarks_empty_default_folder_name)
            ),
            subtitle = stringResource(id = R.string.bookmarks_empty_message)
        )
    }

    editItem?.let { item ->
        BookmarkEditDialog(
            item = item,
            onSubmit = { title, url ->
                viewModel.editBookmark(item, title, url)
            },
            onDismiss = { editItem = null }
        )
    }

    val folderTree by viewModel.folderTree.collectAsState()
    moveItem?.let { item ->
        folderTree?.let { folderTree ->
            BookmarkMoveDialog(
                item = item,
                folderTree = folderTree,
                onSubmit = { to -> viewModel.moveBookmark(item, to) },
                onDismiss = { moveItem = null }
            )
        }
    }
}