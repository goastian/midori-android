package org.midorinext.android.storage.history

import android.database.Cursor
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import mozilla.components.concept.storage.FrecencyThresholdOption
import mozilla.components.concept.storage.VisitType

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(page: Page)

    @Upsert
    suspend fun upsert(page: Page)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNeeded(page: Page)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(visit: Visit)

    @Query("DELETE FROM visits WHERE pageUri = :url AND time = :time")
    suspend fun deleteVisitByKey(url: String, time: Long)

    @Query("DELETE from pages WHERE uri = :uri")
    suspend fun deleteURI(uri: String)

    @Query("DELETE from visits WHERE time >= :timestamp")
    suspend fun deleteVisitsSince(timestamp: Long)

    @Query("DELETE from visits WHERE time BETWEEN :startTime AND :endTime")
    suspend fun deleteVisitsBetween(startTime: Long, endTime: Long)

    @Query("DELETE from visits WHERE time < :time")
    suspend fun deleteVisitsBefore(time: Long)

    @Query("SELECT * FROM visits ORDER BY pageUri, timestampUsec, type")
    suspend fun syncVisits(): List<Visit>

    @Query("SELECT * FROM visits WHERE pageUri = :url AND timestampUsec >= :start AND timestampUsec < :end ORDER BY timestampUsec, type")
    suspend fun syncVisits(url: String, start: Long, end: Long): List<Visit>

    @Query("DELETE FROM visits WHERE pageUri = :url AND timestampUsec >= :start AND timestampUsec < :end")
    suspend fun deleteSyncWindow(url: String, start: Long, end: Long)

    @Insert
    suspend fun recordRemoval(removal: HistoryRemoval)

    @Query("SELECT * FROM sync_history_removals")
    suspend fun syncRemovals(): List<HistoryRemoval>

    @Query("DELETE FROM pages")
    suspend fun deleteAllPages()

    @Query("DELETE FROM pages WHERE NOT EXISTS (SELECT 1 FROM visits WHERE visits.pageUri = pages.uri)")
    suspend fun prunePages()

    @Query("SELECT EXISTS (SELECT 1 FROM visits WHERE pageUri = :url)")
    fun hasVisits(url: String): Boolean

    @Query("SELECT DISTINCT pageUri FROM visits WHERE pageUri IN (:uris)")
    suspend fun getVisitedUris(uris: List<String>): List<String>

    @Query("SELECT * FROM pages WHERE EXISTS (SELECT 1 FROM visits WHERE visits.pageUri = pages.uri)")
    fun getAllPages(): List<Page>

    @Query("SELECT * from pages WHERE uri = :uri")
    fun getPage(uri: String): Page?

    @Query("SELECT * FROM pages " +
            "JOIN visits ON uri = pageUri " +
            "WHERE (time BETWEEN :start AND :end) " +
            "AND (type NOT IN (:excludeTypes))")
    fun getVisitsDetailed(
        start: Long,
        end: Long,
        excludeTypes: List<VisitType>
    ): Map<Page, List<Visit>>

    @Query("SELECT uri, title, previewImageUrl, " +
            "pageUri, type, MAX(time) time, timestampUsec, count " +
            "FROM pages " +
            "JOIN visits ON uri = pageUri " +
            "AND (type NOT IN (:excludeTypes))" +
            "GROUP BY pages.uri " +
            "ORDER BY time DESC, pages.uri ASC " +
            "LIMIT :count OFFSET :offset")
    fun getVisitsPaginated(
        offset: Long,
        count: Long,
        excludeTypes: List<VisitType>
    ): Map<Page, List<Visit>>

    @Query("SELECT * FROM pages " +
            "ORDER BY (" +
                "SELECT COUNT(visits.time) FROM visits " +
                "WHERE visits.pageUri = pages.uri " +
            ") DESC " +
            "LIMIT :numItems")
    fun getTopFrecentSites(
        numItems: Int
    ): List<Page>

    /* @Query("SELECT * FROM pages " +
            "ORDER BY (" +
                "SELECT COUNT(visits.time) as count FROM visits " +
                "WHERE visits.pageUri = pages.uri " +
                "AND count > 1" +
            ") DESC " +
            "LIMIT :numItems")
    fun getTopFrecentSitesSkippingOneTimePages(
        numItems: Int
    ): List<Page> */

    @Query("SELECT pages.* FROM pages " +
            "LEFT JOIN visits ON pages.uri = visits.pageUri " +
            "WHERE pages.uri LIKE '%' || :query || '%' " +
            "OR pages.title LIKE '%' || :query || '%' " +
            "GROUP BY pages.uri " +
            "ORDER BY MAX(visits.time) DESC " +
            "LIMIT :limit")
    fun getSuggestions(
        query: String,
        limit: Int
    ): List<Page>

    @Query("SELECT EXISTS (SELECT * FROM pages)")
    fun hasHistoryFlow() : Flow<Boolean>

    @Query("SELECT pages.* FROM pages JOIN visits ON pages.uri = visits.pageUri " +
        "WHERE (pages.uri LIKE 'https://%' OR pages.uri LIKE 'http://%') " +
        "AND visits.type NOT IN (:excludeTypes) " +
        "GROUP BY pages.uri ORDER BY MAX(visits.time) DESC, pages.uri ASC")
    fun getRecentPages(excludeTypes: List<VisitType>): Cursor

    @Query("SELECT DISTINCT pageUri FROM visits WHERE pageUri IN (:uris) AND type NOT IN (:excludeTypes)")
    suspend fun getPagesWithVisits(uris: List<String>, excludeTypes: List<VisitType>): List<String>
}
