package org.midorinext.android.storage.readinglist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingListRepository @Inject constructor(
    db: ReadingListDatabase
) {
    private val dao = db.readingListDao()

    fun getItemsFlow(query: String): Flow<List<ReadingListItem>> {
        val trimmed = query.trim()
        return if (trimmed.isBlank()) {
            dao.getAllFlow()
        } else {
            dao.searchFlow(trimmed)
        }.flowOn(Dispatchers.IO)
    }

    fun isUrlSavedFlow(url: String): Flow<Boolean> = dao.isUrlSavedFlow(url).flowOn(Dispatchers.IO)

    val hasItemsFlow: Flow<Boolean> = dao.hasItemsFlow().flowOn(Dispatchers.IO)

    suspend fun addOrUpdate(url: String, title: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cleanTitle = title.trim().ifBlank { url }
        val existing = dao.getByUrl(url)
        if (existing == null) {
            dao.insert(
                ReadingListItem(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    title = cleanTitle,
                    addedAt = now,
                    updatedAt = now
                )
            )
        } else {
            dao.update(existing.copy(title = cleanTitle, updatedAt = now))
        }
    }

    suspend fun setRead(id: String, read: Boolean) = withContext(Dispatchers.IO) {
        dao.setRead(id, read, System.currentTimeMillis())
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun deleteByUrl(url: String) = withContext(Dispatchers.IO) {
        dao.deleteByUrl(url)
    }
}
