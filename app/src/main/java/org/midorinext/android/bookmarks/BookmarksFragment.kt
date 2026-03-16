/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.bookmarks

import android.content.Intent
import android.os.Bundle
import org.midorinext.android.browser.BrowserFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import org.midorinext.android.R
import org.midorinext.android.ext.requireComponents

class BookmarksFragment : Fragment() {

    private val bookmarkItems = mutableStateOf<List<BookmarkNode>>(emptyList())
    private val currentFolder = mutableStateOf<String?>(null)
    private val currentFolderTitle = mutableStateOf("Bookmarks")
    private val folderStack = mutableListOf<Pair<String, String>>() // guid -> title

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                BookmarksScreen(
                    bookmarks = bookmarkItems.value,
                    folderTitle = currentFolderTitle.value,
                    isRoot = folderStack.isEmpty(),
                    onBack = { handleBack() },
                    onBookmarkClick = { node -> handleBookmarkClick(node) },
                    onBookmarkLongClick = { node -> /* handled via dropdown */ },
                    onDeleteBookmark = { node -> deleteBookmark(node) },
                    onEditBookmark = { node, newTitle, newUrl -> editBookmark(node, newTitle, newUrl) },
                    onOpenInNewTab = { node -> openInNewTab(node, private = false) },
                    onOpenInPrivateTab = { node -> openInNewTab(node, private = true) },
                    onShareBookmark = { node -> shareBookmark(node) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadBookmarks(currentFolder.value)
    }

    private fun loadBookmarks(guid: String?) {
        val storage = requireComponents.core.bookmarksStorage
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val targetGuid = guid ?: "root________"
                // Use recursive=true so children of folders are populated (for counts)
                val root = storage.getTree(targetGuid, recursive = true).getOrNull()
                val children = root?.children
                    // Filter out separators
                    ?.filter { it.type == BookmarkNodeType.FOLDER || it.type == BookmarkNodeType.ITEM }
                    ?.sortedWith(
                        compareBy<BookmarkNode> { it.type != BookmarkNodeType.FOLDER }
                            .thenBy { it.title?.lowercase() ?: "" }
                    ) ?: emptyList()
                withContext(Dispatchers.Main) {
                    bookmarkItems.value = children
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    bookmarkItems.value = emptyList()
                }
            }
        }
    }

    private fun handleBookmarkClick(node: BookmarkNode) {
        when (node.type) {
            BookmarkNodeType.FOLDER -> {
                folderStack.add(Pair(currentFolder.value ?: "root________", currentFolderTitle.value))
                currentFolder.value = node.guid
                currentFolderTitle.value = node.title ?: "Folder"
                loadBookmarks(node.guid)
            }
            BookmarkNodeType.ITEM -> {
                node.url?.let { url ->
                    requireComponents.useCases.tabsUseCases.addTab(
                        url = url,
                        selectTab = true,
                    )
                    closeBookmarks()
                }
            }
            else -> {}
        }
    }

    private fun handleBack(): Boolean {
        return if (folderStack.isNotEmpty()) {
            val (parentGuid, parentTitle) = folderStack.removeAt(folderStack.size - 1)
            currentFolder.value = if (parentGuid == "root________") null else parentGuid
            currentFolderTitle.value = parentTitle
            loadBookmarks(currentFolder.value)
            true
        } else {
            closeBookmarks()
            true
        }
    }

    private fun deleteBookmark(node: BookmarkNode) {
        val storage = requireComponents.core.bookmarksStorage
        CoroutineScope(Dispatchers.IO).launch {
            storage.deleteNode(node.guid).getOrNull()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                loadBookmarks(currentFolder.value)
            }
        }
    }

    private fun editBookmark(node: BookmarkNode, newTitle: String, newUrl: String) {
        val storage = requireComponents.core.bookmarksStorage
        CoroutineScope(Dispatchers.IO).launch {
            storage.updateNode(
                node.guid,
                mozilla.components.concept.storage.BookmarkInfo(
                    parentGuid = node.parentGuid,
                    position = null,
                    title = newTitle,
                    url = if (node.type == BookmarkNodeType.ITEM) newUrl else null,
                ),
            )
            withContext(Dispatchers.Main) {
                loadBookmarks(currentFolder.value)
            }
        }
    }

    private fun openInNewTab(node: BookmarkNode, private: Boolean) {
        node.url?.let { url ->
            requireComponents.useCases.tabsUseCases.addTab(
                url = url,
                selectTab = true,
                private = private,
            )
            closeBookmarks()
        }
    }

    private fun shareBookmark(node: BookmarkNode) {
        val url = node.url ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, node.title ?: url)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.bookmark_share)))
    }

    private fun closeBookmarks() {
        activity?.supportFragmentManager?.beginTransaction()?.apply {
            setCustomAnimations(
                R.anim.slide_in_left, R.anim.slide_out_right,
                R.anim.slide_in_right, R.anim.slide_out_left,
            )
            replace(R.id.container, BrowserFragment.create())
            commit()
        }
    }

    companion object {
        fun addBookmark(
            fragment: Fragment,
            title: String,
            url: String,
        ) {
            val storage = fragment.requireComponents.core.bookmarksStorage
            CoroutineScope(Dispatchers.IO).launch {
                // Check if already bookmarked
                val existing = storage.getBookmarksWithUrl(url).getOrNull() ?: emptyList()
                if (existing.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            fragment.requireContext(),
                            R.string.bookmark_already_exists,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return@launch
                }

                storage.addItem(
                    parentGuid = "unfiled_____",
                    url = url,
                    title = title,
                    position = null,
                ).getOrNull()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.bookmark_added,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        fun isBookmarked(
            fragment: Fragment,
            url: String,
            callback: (Boolean) -> Unit,
        ) {
            val storage = fragment.requireComponents.core.bookmarksStorage
            CoroutineScope(Dispatchers.IO).launch {
                val existing = storage.getBookmarksWithUrl(url).getOrNull() ?: emptyList()
                withContext(Dispatchers.Main) {
                    callback(existing.isNotEmpty())
                }
            }
        }

        fun toggleBookmark(
            fragment: Fragment,
            title: String,
            url: String,
        ) {
            val storage = fragment.requireComponents.core.bookmarksStorage
            CoroutineScope(Dispatchers.IO).launch {
                val existing = storage.getBookmarksWithUrl(url).getOrNull() ?: emptyList()
                if (existing.isNotEmpty()) {
                    for (item in existing) { storage.deleteNode(item.guid).getOrNull() }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            fragment.requireContext(),
                            R.string.bookmark_removed,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                } else {
                    storage.addItem(
                        parentGuid = "unfiled_____",
                        url = url,
                        title = title,
                        position = null,
                    ).getOrNull()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            fragment.requireContext(),
                            R.string.bookmark_added,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            }
        }
    }
}

// ======================== Compose UI ========================

// Theme colors
private val LightBg = Color(0xFFF5FAF7)
private val LightSurface = Color(0xFFE8F3EC)
private val LightTextPrimary = Color(0xFF0A1510)
private val LightTextSecondary = Color(0xFF3D5348)
private val LightDivider = Color(0x3304A469)

private val DarkBg = Color(0xFF0D1117)
private val DarkSurface = Color(0xFF121D2B)
private val DarkTextPrimary = Color.White
private val DarkTextSecondary = Color(0xFF8B949E)
private val DarkDivider = Color(0x1A06E290)

private val MidoriGreen = Color(0xFF04A469)
private val MidoriGreenBright = Color(0xFF06E290)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkNode>,
    folderTitle: String,
    isRoot: Boolean,
    onBack: () -> Unit,
    onBookmarkClick: (BookmarkNode) -> Unit,
    onBookmarkLongClick: (BookmarkNode) -> Unit,
    onDeleteBookmark: (BookmarkNode) -> Unit,
    onEditBookmark: (BookmarkNode, String, String) -> Unit,
    onOpenInNewTab: (BookmarkNode) -> Unit,
    onOpenInPrivateTab: (BookmarkNode) -> Unit,
    onShareBookmark: (BookmarkNode) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) DarkBg else LightBg
    val surface = if (isDark) DarkSurface else LightSurface
    val textPrimary = if (isDark) DarkTextPrimary else LightTextPrimary
    val textSecondary = if (isDark) DarkTextSecondary else LightTextSecondary
    val divider = if (isDark) DarkDivider else LightDivider
    val accent = if (isDark) MidoriGreenBright else MidoriGreen

    var editingBookmark by remember { mutableStateOf<BookmarkNode?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(id = mozilla.components.ui.icons.R.drawable.mozac_ic_back_24),
                    contentDescription = "Back",
                    tint = textPrimary,
                )
            }

            Text(
                text = folderTitle,
                color = textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(divider),
        )

        if (bookmarks.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bookmarks),
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.bookmarks_empty),
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.bookmarks_empty_subtitle),
                    color = textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(bookmarks, key = { it.guid }) { bookmark ->
                    var showMenu by remember { mutableStateOf(false) }

                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onBookmarkClick(bookmark) },
                                    onLongClick = { showMenu = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bookmark.type == BookmarkNodeType.FOLDER) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_folder),
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                } else {
                                    Text(
                                        text = (bookmark.title?.firstOrNull()?.uppercase() ?: "?"),
                                        color = accent,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bookmark.title ?: bookmark.url ?: "",
                                    color = textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (bookmark.type == BookmarkNodeType.ITEM && bookmark.url != null) {
                                    Text(
                                        text = bookmark.url!!,
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (bookmark.type == BookmarkNodeType.FOLDER) {
                                    val count = bookmark.children
                                        ?.count { it.type == BookmarkNodeType.FOLDER || it.type == BookmarkNodeType.ITEM }
                                        ?: 0
                                    Text(
                                        text = "$count items",
                                        color = textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }

                            // More button
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_menu_dots),
                                    contentDescription = "More",
                                    tint = textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        // Context menu
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            if (bookmark.type == BookmarkNodeType.ITEM) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmark_open_new_tab)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenInNewTab(bookmark)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmark_open_private_tab)) },
                                    onClick = {
                                        showMenu = false
                                        onOpenInPrivateTab(bookmark)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bookmark_share)) },
                                    onClick = {
                                        showMenu = false
                                        onShareBookmark(bookmark)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmark_edit_title)) },
                                onClick = {
                                    showMenu = false
                                    editingBookmark = bookmark
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.bookmark_delete), color = Color(0xFFE53935)) },
                                onClick = {
                                    showMenu = false
                                    onDeleteBookmark(bookmark)
                                },
                            )
                        }
                    }

                    // Item divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 70.dp)
                            .height(1.dp)
                            .background(divider),
                    )
                }
            }
        }
    }

    // Edit dialog
    editingBookmark?.let { bookmark ->
        EditBookmarkDialog(
            bookmark = bookmark,
            onDismiss = { editingBookmark = null },
            onSave = { title, url ->
                onEditBookmark(bookmark, title, url)
                editingBookmark = null
            },
        )
    }
}

@Composable
fun EditBookmarkDialog(
    bookmark: BookmarkNode,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(bookmark.title ?: "") }
    var url by remember { mutableStateOf(bookmark.url ?: "") }
    val isDark = isSystemInDarkTheme()
    val accent = if (isDark) MidoriGreenBright else MidoriGreen

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.bookmark_edit_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.bookmark_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        cursorColor = accent,
                    ),
                )
                if (bookmark.type == BookmarkNodeType.ITEM) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.bookmark_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            cursorColor = accent,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, url) }) {
                Text(stringResource(R.string.bookmark_save), color = accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.customize_addon_collection_cancel))
            }
        },
    )
}
