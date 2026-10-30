package org.midorinext.android.storage.bookmarks

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import androidx.room.withTransaction
import org.midorinext.android.R
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.loader.IconLoader
import mozilla.components.concept.storage.BookmarkInfo
import mozilla.components.concept.storage.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType
import mozilla.components.concept.storage.BookmarksStorage
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeNode
import mozilla.components.concept.storage.bookmarks.InsertableBookmarkTreeRoot
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BookmarksRepository @Inject constructor(
    private val db: BookmarksDatabase,
    @ApplicationContext context: Context
): BookmarksStorage, SuggestionProvider {
    private val dao = db.bookmarksDao()

    val root = BookmarkNode(
        type = BookmarkNodeType.FOLDER,
        guid = ROOT_GUID,
        title = context.getString(R.string.bookmarks),
        parentGuid = null,
        position = null,
        url = null,
        dateAdded = 0,
        lastModified = 0,
        children = null
    )

    fun getBookmarksInFolderFlow(guid: String): Flow<List<BookmarkNode>> =
        dao.getBookmarksInFolderFlow(guid).map { list ->
            list.map { it.toMozillaBookmarkNode() }
        }.flowOn(Dispatchers.IO)

    fun isUrlBookmarkedFlow(url: String): Flow<Boolean> =
        dao.isUrlBookmarkedFlow(url).flowOn(Dispatchers.IO)

    fun bookmarksPager(guid: String) = Pager(pageConfig()) {
        BookmarksPagingSource(db) { offset, count -> dao.getChildrenPage(guid, offset, count) }
    }.flow.map { data -> data.map { it.toMozillaBookmarkNode() } }

    fun folderChoices(guid: String) = Pager(pageConfig()) {
        BookmarksPagingSource(db) { offset, count -> dao.getFolderChoices(guid, offset, count) }
    }.flow

    suspend fun folderAncestors(guid: String?): List<BookmarkFolderChoice> = withContext(Dispatchers.IO) {
        val path = mutableListOf<BookmarkFolderChoice>()
        val seen = mutableSetOf<String>()
        var current = guid
        while (current != null && current != root.guid && seen.add(current)) {
            currentCoroutineContext().ensureActive()
            val node = dao.get(current) ?: break
            path += BookmarkFolderChoice(node.guid, node.parentGuid, node.title, true)
            current = node.parentGuid
        }
        listOf(BookmarkFolderChoice(root.guid, null, root.title, true)) + path.asReversed()
    }

    private fun pageConfig() = PagingConfig(
        pageSize = 50, initialLoadSize = 100, prefetchDistance = 25,
        maxSize = 300, enablePlaceholders = false
    )

    override suspend fun addFolder(
        parentGuid: String,
        title: String,
        position: UInt?
    ): Result<String> = withContext(Dispatchers.IO) {
        val guid = getNewGuid()
        dao.insert(BookmarkNode(
            guid = guid,
            type = BookmarkNodeType.FOLDER,
            parentGuid = parentGuid,
            title = title,
            position = position?.toInt(),
            url = null,
            dateAdded = System.currentTimeMillis()
        ))
        Result.success(guid)
    }

    override suspend fun addItem(
        parentGuid: String,
        url: String,
        title: String,
        position: UInt?
    ): Result<String> = withContext(Dispatchers.IO) {
        val guid = getNewGuid()
        dao.insert(BookmarkNode(
            guid = guid,
            type = BookmarkNodeType.ITEM,
            parentGuid = parentGuid,
            title = title,
            position = position?.toInt(),
            url = url,
            dateAdded = System.currentTimeMillis()
        ))
        Result.success(guid)
    }

    override suspend fun addSeparator(parentGuid: String, position: UInt?): Result<String> = withContext(Dispatchers.IO) {
        val guid = getNewGuid()
        dao.insert(BookmarkNode(
            guid = guid,
            type = BookmarkNodeType.SEPARATOR,
            parentGuid = parentGuid,
            title = null,
            position = position?.toInt(),
            url = null,
            dateAdded = System.currentTimeMillis()
        ))
        Result.success(guid)
    }

    override suspend fun insertTree(tree: InsertableBookmarkTreeRoot): Result<String> =
        insertTreeWithProgress(tree)

    suspend fun insertTreeWithProgress(
        tree: InsertableBookmarkTreeRoot,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        var inserted = 0
        try {
            val guid = db.withTransaction {
                val insertedGuid = insertTreeNode(tree.rootFolder, tree.parentGuid, tree.rootFolder.position) {
                    inserted++
                    if (inserted % 100 == 0) onProgress(inserted)
                }
                onProgress(inserted)
                insertedGuid
            }
            Result.success(guid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun deleteNode(guid: String): Result<Boolean> = deleteNodeWithProgress(guid)

    suspend fun deleteNodeWithProgress(guid: String, onProgress: (Int) -> Unit = {}): Result<Boolean> = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (!dao.alreadyExists(guid)) return@withTransaction Result.success(false)
            val pending = java.util.ArrayDeque<String>().apply { add(guid) }
            val visited = mutableSetOf(guid)
            var deleted = 0
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val id = pending.last
                val children = dao.getChildGuids(id, 100)
                if (children.isEmpty()) {
                    dao.deleteByGuid(id)
                    pending.removeLast()
                    deleted++
                    if (deleted % 100 == 0) onProgress(deleted)
                } else {
                    children.forEach { child ->
                        check(visited.add(child)) { "Cyclic bookmark hierarchy" }
                        pending.add(child)
                    }
                }
            }
            onProgress(deleted)
            Result.success(true)
        }
    }

    override suspend fun countBookmarksInTrees(guids: List<String>): UInt {
        // TODO, although not used
        return 0u
    }

    suspend fun deleteBookmarksByUrl(url: String) = withContext(Dispatchers.IO) {
        dao.deleteByUrl(url)
    }

    override suspend fun getBookmark(guid: String): Result<BookmarkNode?> = withContext(Dispatchers.IO) {
        Result.success(dao.get(guid)?.toMozillaBookmarkNode())
    }

    override suspend fun getBookmarksWithUrl(url: String): Result<List<BookmarkNode>> = withContext(Dispatchers.IO) {
        Result.success(dao.getByUrl(url).map { it.toMozillaBookmarkNode() })
    }

    override suspend fun getRecentBookmarks(
        limit: Int,
        maxAge: Long?,
    ): Result<List<BookmarkNode>> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val oldestAllowedDate = maxAge?.let { currentTime - it } ?: 0
        Result.success(dao.getRecent(oldestAllowedDate, currentTime, limit).map { it.toMozillaBookmarkNode() })
    }

    override suspend fun getTree(guid: String, recursive: Boolean): Result<BookmarkNode?> = withContext(Dispatchers.IO) {
        val children: List<BookmarkNode> = dao.getChildren(guid).mapNotNull { child ->
            if (recursive) {
                this@BookmarksRepository.getTree(child.guid, true).getOrNull()
            } else {
                child.toMozillaBookmarkNode()
            }
        }
        Result.success(when (guid) {
            root.guid -> root.copy(children = children)
            else -> dao.get(guid)?.toMozillaBookmarkNode(children)
        })
    }

    override suspend fun searchBookmarks(query: String, limit: Int): Result<List<BookmarkNode>> = withContext(Dispatchers.IO) {
        Result.success(dao.search(query, limit).map { it.toMozillaBookmarkNode() })
    }

    override suspend fun updateNode(guid: String, info: BookmarkInfo): Result<Unit> = withContext(Dispatchers.IO) {
        db.withTransaction {
            if (info.parentGuid != null) {
                val seen = mutableSetOf<String>()
                var parent: String? = info.parentGuid
                while (parent != null && parent != root.guid) {
                    currentCoroutineContext().ensureActive()
                    if (parent == guid || !seen.add(parent)) return@withTransaction Result.failure(IllegalArgumentException("Cyclic bookmark move"))
                    val ancestor = dao.get(parent) ?: return@withTransaction Result.failure(IllegalArgumentException("Missing bookmark parent"))
                    if (ancestor.type != BookmarkNodeType.FOLDER) return@withTransaction Result.failure(IllegalArgumentException("Invalid bookmark parent"))
                    parent = ancestor.parentGuid
                }
            }
            val bookmark = dao.get(guid) ?: return@withTransaction Result.failure(IllegalArgumentException("Bookmark not found"))
            info.parentGuid?.let { bookmark.parentGuid = it }
            info.title?.let { bookmark.title = it }
            info.url?.let { bookmark.url = it }
            info.position?.let { bookmark.position = it.toInt() }
            dao.update(bookmark)
            Result.success(Unit)
        }
    }

    override suspend fun getSuggestions(text: String): List<Suggestion> = withContext(Dispatchers.IO) {
        dao.search(text, 1).map { // TODO make bookmarks suggestions limit dynamic
            Suggestion.OpenTabSuggestion(this@BookmarksRepository, text, it.title, it.url)
        }
    }

    private suspend fun getNewGuid(): String {
        var guid: String
        do {
            guid = UUID.randomUUID().toString()
        } while (dao.alreadyExists(guid))
        return guid
    }

    private suspend fun insertTreeNode(
        node: InsertableBookmarkTreeNode,
        parentGuid: String,
        fallbackPosition: UInt?,
        onInserted: () -> Unit
    ): String {
        currentCoroutineContext().ensureActive()
        val guid = getNewGuid()
        val position = node.position ?: fallbackPosition
        when (node) {
            is InsertableBookmarkTreeNode.Folder -> {
                dao.insert(org.midorinext.android.storage.bookmarks.BookmarkNode(
                    guid = guid,
                    type = BookmarkNodeType.FOLDER,
                    parentGuid = parentGuid,
                    position = position?.toInt(),
                    title = node.title,
                    url = null,
                    dateAdded = node.dateAddedTimestamp
                ))
                node.children.forEachIndexed { index, child ->
                    insertTreeNode(child, guid, child.position ?: index.toUInt(), onInserted)
                }
            }
            is InsertableBookmarkTreeNode.Item -> {
                dao.insert(org.midorinext.android.storage.bookmarks.BookmarkNode(
                    guid = guid,
                    type = BookmarkNodeType.ITEM,
                    parentGuid = parentGuid,
                    position = position?.toInt(),
                    title = node.title,
                    url = node.url,
                    dateAdded = node.dateAddedTimestamp
                ))
            }
            is InsertableBookmarkTreeNode.Separator -> {
                dao.insert(org.midorinext.android.storage.bookmarks.BookmarkNode(
                    guid = guid,
                    type = BookmarkNodeType.SEPARATOR,
                    parentGuid = parentGuid,
                    position = position?.toInt(),
                    title = null,
                    url = null,
                    dateAdded = node.dateAddedTimestamp
                ))
            }
        }
        onInserted()
        return guid
    }

    // Not applicable
    override suspend fun warmUp() {}
    override suspend fun runMaintenance(dbSizeLimit: UInt) {}

    companion object {
        private const val ROOT_GUID = "bookmarks_root"
    }
}

fun org.midorinext.android.storage.bookmarks.BookmarkNode.toMozillaBookmarkNode(
    children: List<BookmarkNode>? = null
): BookmarkNode {
    return BookmarkNode(
        this.type,
        this.guid,
        this.parentGuid,
        this.position?.toUInt(),
        this.title,
        this.url,
        this.dateAdded,
        lastModified = 0,
        children
    )
}
