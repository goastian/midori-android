package contentBlocker

import org.midorinext.android.contentBlocker.cacheDb.BlockListType
import org.midorinext.android.contentBlocker.cacheDb.ContentBlockerCacheRepositoryInterface
import org.midorinext.android.contentBlocker.cacheDb.Domain
import org.midorinext.android.contentBlocker.cacheDb.TabId
import org.midorinext.android.contentBlocker.cacheDb.Url

class ContentBlockerCacheRepositoryMock(): ContentBlockerCacheRepositoryInterface {
    override suspend fun getUrl(hash: String, type: BlockListType): Url? = null
    override suspend fun getDomain(hash: String, type: BlockListType): Domain? = null
    override suspend fun getTabId(tabId: String): TabId? = null
    override suspend fun deleteTabId(tabId: TabId) {}
    override suspend fun saveUrl(hash: String, blocked: Boolean, type: BlockListType) {}
    override suspend fun saveDomain(hash: String, blocked: Boolean, type: BlockListType) {}
    override suspend fun saveTabId(tabId: TabId) {}
    override suspend fun runMaintenance() {}
}