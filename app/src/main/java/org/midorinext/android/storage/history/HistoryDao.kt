package org.midorinext.android.storage.history

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

    @Insert
    suspend fun insert(visit: Visit)

    @Delete(entity = Visit::class)
    suspend fun deleteVisitByKey(visitKey: VisitKey)

    @Query("DELETE from pages WHERE uri = :uri")
    suspend fun deleteURI(uri: String)

    @Query("DELETE from visits WHERE time >= :timestamp")
    suspend fun deleteVisitsSince(timestamp: Long)

    @Query("DELETE from visits WHERE time BETWEEN :startTime AND :endTime")
    suspend fun deleteVisitsBetween(startTime: Long, endTime: Long)

    @Query("DELETE from visits WHERE time < :time")
    suspend fun deleteVisitsBefore(time: Long)

    @Query("SELECT * from pages")
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
            "pageUri, type, MAX(time) time " +
            "FROM pages " +
            "JOIN visits ON uri = pageUri " +
            "AND (type NOT IN (:excludeTypes))" +
            "GROUP BY pages.uri " +
            "ORDER BY time DESC " +
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

    @Query("SELECT * FROM pages " +
            "WHERE uri LIKE '%' || :query || '%' " +
            "OR title LIKE '%' || :query || '%'" +
            "LIMIT :limit")
    fun getSuggestions(
        query: String,
        limit: Int
    ): List<Page>

    @Query("SELECT EXISTS (SELECT * FROM pages)")
    fun hasHistoryFlow() : Flow<Boolean>
}