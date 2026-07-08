package org.midorinext.android.contentBlocker.cacheDb

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ContentBlockerCacheDao {
    @Insert suspend fun insert(domain: Domain)
    @Insert suspend fun insert(url: Url)
    @Upsert suspend fun upsert(tabId: TabId)

    @Query("SELECT * from domains WHERE hash = :hash AND type = :type")
    fun getDomain(hash: String, type: BlockListType): Domain?
    @Query("SELECT * from urls WHERE hash = :hash AND type = :type")
    fun getUrl(hash: String, type: BlockListType): Url?
    @Query("SELECT * from tab_ids WHERE tabId = :tabId")
    fun getTabId(tabId: String): TabId?

    @Query("DELETE from domains WHERE time < :before")
    suspend fun deleteDomainsBefore(before: Long)

    @Query("DELETE from urls WHERE time < :before")
    suspend fun deleteUrlsBefore(before: Long)

    @Delete
    suspend fun deleteTabId(tabId: TabId)
}