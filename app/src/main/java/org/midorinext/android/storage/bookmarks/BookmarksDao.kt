package org.midorinext.android.storage.bookmarks

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import mozilla.components.concept.storage.BookmarkNodeType

data class BookmarkFolderChoice(
    val guid: String,
    val parentGuid: String?,
    val title: String?,
    val hasChildren: Boolean
)

@Dao
interface BookmarksDao {
    @Query("SELECT * FROM bookmark_node WHERE guid>:after ORDER BY guid LIMIT :limit")
    suspend fun syncNodePage(after: String, limit: Int): List<BookmarkNode>

    @Query("SELECT * FROM bookmark_node WHERE parentGuid=:parent AND position IS NULL AND guid>:after ORDER BY guid LIMIT :limit")
    suspend fun syncNullPositionPage(parent: String, after: String, limit: Int): List<BookmarkNode>

    @Query("SELECT COUNT(*) FROM bookmark_node WHERE parentGuid IS :parent AND position IS NULL AND (dateAdded<:added OR (dateAdded=:added AND guid<:guid))")
    suspend fun syncFallbackIndex(parent: String?, added: Long, guid: String): Int

    @Query("SELECT * FROM bookmark_node WHERE parentGuid = :guid " +
        "ORDER BY CASE type WHEN 'SEPARATOR' THEN 0 WHEN 'FOLDER' THEN 1 ELSE 2 END, " +
        "title COLLATE LOCALIZED, guid LIMIT :count OFFSET :offset")
    suspend fun getChildrenPage(guid: String, offset: Int, count: Int): List<BookmarkNode>

    @Query("SELECT folder.guid, folder.parentGuid, folder.title, " +
        "EXISTS(SELECT 1 FROM bookmark_node child WHERE child.parentGuid = folder.guid AND child.type = 'FOLDER') AS hasChildren " +
        "FROM bookmark_node folder WHERE folder.parentGuid = :guid AND folder.type = 'FOLDER' " +
        "ORDER BY folder.title COLLATE LOCALIZED, folder.guid LIMIT :count OFFSET :offset")
    suspend fun getFolderChoices(guid: String, offset: Int, count: Int): List<BookmarkFolderChoice>

    @Query("SELECT guid FROM bookmark_node WHERE parentGuid = :guid ORDER BY guid LIMIT :count")
    suspend fun getChildGuids(guid: String, count: Int): List<String>

    @Query("SELECT * FROM bookmark_node ORDER BY parentGuid, position, dateAdded, guid")
    suspend fun syncNodes(): List<BookmarkNode>

    @Upsert
    suspend fun syncUpsert(bookmark: BookmarkNode)

    @Insert
    suspend fun insert(bookmark: BookmarkNode)

    @Update
    suspend fun update(bookmark: BookmarkNode)

    @Query("DELETE from bookmark_node WHERE guid = :guid")
    suspend fun deleteByGuid(guid: String)

    @Query("DELETE from bookmark_node WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("SELECT * FROM bookmark_node WHERE guid = :guid")
    suspend fun get(guid: String): BookmarkNode?

    @Query("SELECT * FROM bookmark_node WHERE parentGuid = :guid")
    fun getBookmarksInFolderFlow(guid: String): Flow<List<BookmarkNode>>

    @Query("SELECT * FROM bookmark_node WHERE url = :url")
    suspend fun getByUrl(url: String): List<BookmarkNode>

    @Query("SELECT EXISTS (SELECT * FROM bookmark_node WHERE url = :url)")
    fun isUrlBookmarkedFlow(url: String): Flow<Boolean>

    @Query("SELECT * FROM bookmark_node " +
            "WHERE dateAdded BETWEEN :maxAge AND :currentTime " +
            "LIMIT :limit")
    suspend fun getRecent(maxAge: Long, currentTime: Long, limit: Int): List<BookmarkNode>

    @Query("SELECT * FROM bookmark_node " +
            "WHERE url LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%'" +
            "ORDER BY dateAdded DESC " +
            "LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<BookmarkNode>

    @Query("SELECT * FROM bookmark_node " +
            "WHERE parentGuid = :guid " +
            "ORDER BY position ")
    suspend fun getChildren(guid: String): List<BookmarkNode>

    @Query("SELECT * FROM bookmark_node " +
            "WHERE parentGuid = :guid " +
            "AND type = :typeFilter " +
            "ORDER BY position ")
    suspend fun getChildren(guid: String, typeFilter: BookmarkNodeType): List<BookmarkNode>

    @Query("SELECT EXISTS (SELECT * FROM bookmark_node WHERE guid = :guid)")
    suspend fun alreadyExists(guid: String) : Boolean
}