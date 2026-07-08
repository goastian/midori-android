package org.midorinext.android.contentBlocker.cacheDb

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentBlockerCacheRepository @Inject constructor(
    db: ContentBlockerCacheDatabase
): ContentBlockerCacheRepositoryInterface {
    private val dao = db.contentBlockerCacheDao()

    override suspend fun getUrl(hash: String, type: BlockListType): Url? = withContext(Dispatchers.IO) {
        return@withContext dao.getUrl(hash, type)
    }

    override suspend fun getDomain(hash: String, type: BlockListType): Domain? = withContext(Dispatchers.IO) {
        return@withContext dao.getDomain(hash, type)
    }

    override suspend fun getTabId(tabId: String): TabId? = withContext(Dispatchers.IO) {
        return@withContext dao.getTabId(tabId)
    }

    override suspend fun deleteTabId(tabId: TabId) = withContext(Dispatchers.IO) {
        dao.deleteTabId(tabId)
    }

    override suspend fun saveUrl(hash: String, blocked: Boolean, type: BlockListType) = withContext(Dispatchers.IO) {
        try {
            dao.insert(Url(hash, type, blocked, System.currentTimeMillis()))
        } catch (e: SQLiteConstraintException) {
            Log.e("ContentBlockerCache", "Could not save URL to cache DB", e)
        }
    }

    override suspend fun saveDomain(hash: String, blocked: Boolean, type: BlockListType) = withContext(Dispatchers.IO) {
        try {
            dao.insert(Domain(hash, type, blocked, System.currentTimeMillis()))
        } catch (e: SQLiteConstraintException) {
            Log.e("ContentBlockerCache", "Could not save Domain to cache DB", e)
        }
    }

    override suspend fun saveTabId(tabId: TabId) {
        dao.upsert(tabId)
    }

    override suspend fun runMaintenance() = withContext(Dispatchers.IO) {
        val validTime = System.currentTimeMillis() - CACHE_VALIDITY_LIFETIME
        dao.deleteDomainsBefore(validTime)
        dao.deleteUrlsBefore(validTime)
    }

    companion object {
        const val CACHE_VALIDITY_LIFETIME: Long = 48 * 60 * 60 * 1000 // 48h in milliseconds
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ContentBlockerCacheRepositoryModule {
    @Singleton
    @Provides
    fun provideContentBlockerCacheRepository(
        contentBlocker: ContentBlockerCacheRepository
    ): ContentBlockerCacheRepositoryInterface = contentBlocker
}