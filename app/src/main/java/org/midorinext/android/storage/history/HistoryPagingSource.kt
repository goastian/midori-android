package org.midorinext.android.storage.history

import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.concept.storage.VisitInfo
import mozilla.components.concept.storage.VisitType

class HistoryPagingSource(
    private val historyStorage: HistoryStorage
): PagingSource<Int, VisitInfo>() {
    override fun getRefreshKey(state: PagingState<Int, VisitInfo>): Int {
        return ((state.anchorPosition ?: 0) - state.config.initialLoadSize / 2).coerceAtLeast(0)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VisitInfo> {
        val page = params.key ?: 0
        val offset: Long = page * RESULT_PER_PAGE
        val data = withContext(Dispatchers.IO) {
            historyStorage.getVisitsPaginated(offset, RESULT_PER_PAGE, listOf(
                VisitType.REDIRECT_PERMANENT,
                VisitType.REDIRECT_TEMPORARY,
                VisitType.DOWNLOAD
            ))
        }
        return LoadResult.Page(
            data = data,
            prevKey = if (page == 0) null else (page - 1),
            nextKey = if (data.isEmpty()) null else (page + 1)
        )
    }

    companion object {
        const val RESULT_PER_PAGE: Long = 50
    }
}