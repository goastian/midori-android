package org.midorinext.android.ui.bookmarks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.concept.storage.BookmarkInfo
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.ktx.kotlin.toNormalizedUrl
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BookmarksScreenViewModel @Inject constructor(
    private val bookmarksRepository: BookmarksRepository,
    private val tabsUseCases: TabsUseCases,
    val browserIcons: BrowserIcons
): ViewModel() {
    var folderGuid: String by mutableStateOf(bookmarksRepository.root.guid)
        private set

    val folder = snapshotFlow { folderGuid }
        .mapLatest { bookmarksRepository.getBookmark(folderGuid).getOrNull() ?: bookmarksRepository.root }
        .filterNotNull()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = bookmarksRepository.root
        )

    val isRootFolder = snapshotFlow { folderGuid }
        .map { it == bookmarksRepository.root.guid }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = true
        )

    val bookmarks = snapshotFlow { folderGuid }
        .flatMapLatest { bookmarksRepository.getBookmarksInFolderFlow(it) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = listOf()
        )

    private var folderTreeStateFlow: MutableStateFlow<BookmarkNode?> = MutableStateFlow(null)
    val folderTree = folderTreeStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    init {
        loadFolderTree()
    }

    private fun loadFolderTree() {
        viewModelScope.launch(Dispatchers.IO) {
            folderTreeStateFlow.update {
                bookmarksRepository.getFolderTree(bookmarksRepository.root.guid)
            }
        }
    }

    fun visitFolder(folderGuid: String) {
        this.folderGuid = folderGuid
    }

    fun addFolder(title: String, parentGuid: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarksRepository.addFolder(parentGuid ?: bookmarksRepository.root.guid, title)
        }.invokeOnCompletion { this.loadFolderTree() }
    }

    fun deleteBookmark(item: BookmarkNode) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarksRepository.deleteNode(item.guid)
        }.invokeOnCompletion { this.loadFolderTree() }
    }

    fun editBookmark(item: BookmarkNode, title: String, url: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarksRepository.updateNode(item.guid, BookmarkInfo(
                title = title,
                url = url,
                parentGuid = null,
                position = null
            ))
        }.invokeOnCompletion { this.loadFolderTree() }
    }

    fun moveBookmark(item: BookmarkNode, toGuid: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarksRepository.updateNode(item.guid, BookmarkInfo(
                parentGuid = toGuid ?: bookmarksRepository.root.guid,
                title = null,
                url = null,
                position = null
            ))
        }.invokeOnCompletion { this.loadFolderTree() }
    }

    fun openBookmarkTab(item: BookmarkNode, private: Boolean = false) {
        item.url?.let {
            tabsUseCases.addTab(it.toNormalizedUrl(), private = private)
        }
    }
}