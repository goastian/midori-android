package org.midorinext.android.storage.bookmarks

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BookmarksPagingSource<T : Any>(
    private val db: BookmarksDatabase,
    private val query: suspend (offset: Int, count: Int) -> List<T>
) : PagingSource<Int, T>() {
    private val observer = object : InvalidationTracker.Observer("bookmark_node") {
        override fun onInvalidated(tables: Set<String>) { invalidate() }
    }

    init {
        db.invalidationTracker.addWeakObserver(observer)
        registerInvalidatedCallback { db.invalidationTracker.removeObserver(observer) }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page ->
            ((page.prevKey ?: 0) + page.data.size / 2 - state.config.initialLoadSize / 2).coerceAtLeast(0)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val boundary = params.key ?: 0
        val offset = if (params is LoadParams.Prepend) (boundary - params.loadSize).coerceAtLeast(0) else boundary
        val count = if (params is LoadParams.Prepend) boundary - offset else params.loadSize
        return try {
            val data = withContext(Dispatchers.IO) { query(offset, count) }
            LoadResult.Page(data, offset.takeIf { it > 0 }, (offset + data.size).takeIf { data.size == count && data.isNotEmpty() })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            LoadResult.Error(error)
        }
    }
}
