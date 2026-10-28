package org.midorinext.android.storage.bookmarks

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import mozilla.components.concept.storage.BookmarkNodeType

@Dao
interface BookmarksDao {
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