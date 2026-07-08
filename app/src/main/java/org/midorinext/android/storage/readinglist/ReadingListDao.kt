package org.midorinext.android.storage.readinglist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReadingListItem)

    @Update
    suspend fun update(item: ReadingListItem)

    @Query("SELECT * FROM reading_list_item WHERE url = :url LIMIT 1")
    suspend fun getByUrl(url: String): ReadingListItem?

    @Query("SELECT EXISTS(SELECT 1 FROM reading_list_item WHERE url = :url LIMIT 1)")
    fun isUrlSavedFlow(url: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM reading_list_item LIMIT 1)")
    fun hasItemsFlow(): Flow<Boolean>

    @Query("SELECT * FROM reading_list_item ORDER BY read ASC, addedAt DESC")
    fun getAllFlow(): Flow<List<ReadingListItem>>

    @Query("""
        SELECT * FROM reading_list_item
        WHERE title LIKE '%' || :query || '%'
           OR url LIKE '%' || :query || '%'
        ORDER BY read ASC, addedAt DESC
    """)
    fun searchFlow(query: String): Flow<List<ReadingListItem>>

    @Query("UPDATE reading_list_item SET read = :read, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRead(id: String, read: Boolean, updatedAt: Long)

    @Query("DELETE FROM reading_list_item WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reading_list_item WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}
