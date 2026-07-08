package org.midorinext.android.storage.bookmarks

import android.content.Context
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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BookmarksRepository @Inject constructor(
    db: BookmarksDatabase,
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

    suspend fun getFolderTree(guid: String): BookmarkNode? = withContext(Dispatchers.IO) {
        val children = dao.getChildren(guid, BookmarkNodeType.FOLDER)
            .mapNotNull { child -> this@BookmarksRepository.getFolderTree(child.guid) }
        when (guid) {
            root.guid -> root.copy(children = children)
            else -> dao.get(guid)?.toMozillaBookmarkNode(children)
        }
    }

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

    override suspend fun deleteNode(guid: String): Result<Boolean> = withContext(Dispatchers.IO) {
         val exists = dao.alreadyExists(guid)
         if (exists) dao.deleteByGuid(guid)
        Result.success(exists)
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
        currentTime: Long
    ): Result<List<BookmarkNode>> = withContext(Dispatchers.IO) {
        Result.success(dao.getRecent(maxAge ?: 0, currentTime, limit).map { it.toMozillaBookmarkNode() })
    }

    override suspend fun getTree(guid: String, recursive: Boolean): Result<BookmarkNode?> = withContext(Dispatchers.IO) {
        val children = dao.getChildren(guid).mapNotNull { child ->
            if (recursive) {
                this@BookmarksRepository.getTree(child.guid, true)
            } else {
                child.toMozillaBookmarkNode()
            }
        }
        Result.success(when (guid) {
            root.guid -> root.copy(children = children as List<BookmarkNode>?)
            else -> dao.get(guid)?.toMozillaBookmarkNode(children as List<BookmarkNode>?)
        })
    }

    override suspend fun searchBookmarks(query: String, limit: Int): Result<List<BookmarkNode>> = withContext(Dispatchers.IO) {
        Result.success(dao.search(query, limit).map { it.toMozillaBookmarkNode() })
    }

    override suspend fun updateNode(guid: String, info: BookmarkInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val bookmark = dao.get(guid)
        bookmark?.let { b ->
            info.parentGuid?.let { b.parentGuid = it }
            info.title?.let { b.title = it }
            info.url?.let { b.url = it }
            info.position?.let { b.position = it.toInt() }
            dao.update(b)
            return@withContext Result.success(Unit)
        }
        return@withContext Result.failure(Throwable("Bookmark not found"))
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