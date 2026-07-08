package org.midorinext.android.storage.history

import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.ext.isMidoriUrl
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.FrecencyThresholdOption
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.SearchResult
import mozilla.components.concept.storage.TopFrecentSiteInfo
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.concept.storage.VisitType
import javax.inject.Inject
import javax.inject.Singleton

// TODO add search terms history

@Singleton
class HistoryRepository @Inject constructor(
    private val db: HistoryDatabase,
    private val contentBlockerState: ContentBlockerState
): HistoryStorage, SuggestionProvider {
    private val dao = db.historyDao()

    val hasHistoryFlow = dao.hasHistoryFlow().flowOn(Dispatchers.IO)

    override fun canAddUri(uri: String): Boolean =
        !uri.isMidoriUrl() && !uri.startsWith("moz-extension://") && uri != "about:blank"
                && contentBlockerState.status == ContentBlockerState.Status.ALLOWED

    override suspend fun recordVisit(uri: String, visit: PageVisit) = withContext(Dispatchers.IO) {
        dao.insertIfNeeded(Page(uri))
        dao.insert(Visit(uri, System.currentTimeMillis(), visit.visitType))
        runMaintenance(0U)
    }

    suspend fun recordVisitWithTimestamp(uri: String, visit: PageVisit, timestamp: Long) = withContext(Dispatchers.IO) {
        if (System.currentTimeMillis() - timestamp < TIME_LIMIT) {
            dao.insertIfNeeded(Page(uri))
            dao.insert(Visit(uri, timestamp, visit.visitType))
        }
    }

    override suspend fun recordObservation(uri: String, observation: PageObservation) = withContext(Dispatchers.IO) {
        val page = dao.getPage(uri) ?: Page(uri)
        observation.title?.let { page.title = it }
        observation.previewImageUrl?.let { page.previewImageUrl = it }
        dao.upsert(page)
    }

    override suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    override suspend fun deleteVisit(url: String, timestamp: Long) = withContext(Dispatchers.IO) {
        dao.deleteVisitByKey(VisitKey(url, timestamp))
    }

    override suspend fun deleteVisitsBetween(startTime: Long, endTime: Long) = withContext(Dispatchers.IO) {
        dao.deleteVisitsBetween(startTime, endTime)
    }

    override suspend fun deleteVisitsFor(url: String) = withContext(Dispatchers.IO) {
        dao.deleteURI(url)
    }

    override suspend fun deleteVisitsSince(since: Long) = withContext(Dispatchers.IO) {
        dao.deleteVisitsSince(since)
    }

    override suspend fun getDetailedVisits(
        start: Long,
        end: Long,
        excludeTypes: List<VisitType>
    ): List<VisitInfo> = withContext(Dispatchers.IO) {
        dao.getVisitsDetailed(start, end, excludeTypes).getVisitInfos()
    }


    override fun getSuggestions(query: String, limit: Int): List<SearchResult> {
        TODO("should run this on background thread. Not done yet as it's unused in our app")
        // dao.getSuggestions(query, limit)
        //     .mapIndexed { index, page -> SearchResult(
        //         id = page.uri,
        //         url = page.uri,
        //         title = page.title,
        //         score = index // Not really relevant. Should rank it using levenshteinDistance
        //     ) }
    }

    override suspend fun getSuggestions(text: String): List<Suggestion> = withContext(Dispatchers.IO) {
        // TODO use levenshtein distance to match and rank suggestions
        //  https://github.com/mozilla-mobile/firefox-android/blob/main/android-components/components/support/utils/src/main/java/mozilla/components/support/utils/StorageUtils.kt
        dao.getSuggestions(query = text, limit = 2) // TODO History suggestions limit should be a parameter
            .map { Suggestion.OpenTabSuggestion(
                provider = this@HistoryRepository,
                search = text,
                title = it.title,
                url = it.uri
            ) }
    }

    override suspend fun getTopFrecentSites(
        numItems: Int,
        frecencyThreshold: FrecencyThresholdOption
    ): List<TopFrecentSiteInfo> {
        return listOf()
        // TODO history top frequent sites
        /* return (if (frecencyThreshold == FrecencyThresholdOption.NONE) dao.getTopFrecentSites(numItems)
        else dao.getTopFrecentSitesSkippingOneTimePages(numItems))
            .map { TopFrecentSiteInfo(it.uri, it.title) } */
    }

    override suspend fun getVisited(): List<String> = withContext(Dispatchers.IO) {
        dao.getAllPages().map { it.uri }
    }

    override suspend fun getVisited(uris: List<String>): List<Boolean> = withContext(Dispatchers.IO) {
        uris.map { dao.getPage(it) != null }
    }

    override suspend fun getVisitsPaginated(
        offset: Long,
        count: Long,
        excludeTypes: List<VisitType>
    ): List<VisitInfo> = withContext(Dispatchers.IO) {
        dao.getVisitsPaginated(offset, count, excludeTypes).getVisitInfos()
    }

    private fun Map<Page, List<Visit>>.getVisitInfos() = this
        .flatMap { (page, visits) ->
            visits.map { visit ->
                VisitInfo(
                    page.uri,
                    page.title,
                    visit.time,
                    visit.type,
                    page.previewImageUrl,
                    false
                )
            }
        }

    override suspend fun runMaintenance(dbSizeLimit: UInt) {
        dao.deleteVisitsBefore(System.currentTimeMillis() - TIME_LIMIT)
    }

    /*
      Not applicable.
     */
    override suspend fun warmUp() {}

    companion object {
        const val TIME_LIMIT = 1000L * 60 * 60 * 24 * 180 // 180 days ~ 6 months
    }
}