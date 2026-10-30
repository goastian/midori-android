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
) : PagingSource<Int, VisitInfo>() {
    override fun getRefreshKey(state: PagingState<Int, VisitInfo>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.let { page ->
                ((page.prevKey ?: 0) + page.data.size / 2 - state.config.initialLoadSize / 2).coerceAtLeast(0)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VisitInfo> {
        val boundary = params.key ?: 0
        val offset = if (params is LoadParams.Prepend) (boundary - params.loadSize).coerceAtLeast(0) else boundary
        val count = if (params is LoadParams.Prepend) boundary - offset else params.loadSize
        return try {
            val data = withContext(Dispatchers.IO) {
                historyStorage.getVisitsPaginated(offset.toLong(), count.toLong(), EXCLUDED_TYPES)
            }
            LoadResult.Page(
                data = data,
                prevKey = offset.takeIf { it > 0 },
                nextKey = (offset + data.size).takeIf { data.size == count && data.isNotEmpty() }
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }

    companion object {
        const val RESULT_PER_PAGE: Long = 50
        private val EXCLUDED_TYPES = listOf(
            VisitType.REDIRECT_PERMANENT, VisitType.REDIRECT_TEMPORARY, VisitType.DOWNLOAD
        )
    }
}
