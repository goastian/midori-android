package org.midorinext.android.contentBlocker.cacheDb

interface ContentBlockerCacheRepositoryInterface {
    suspend fun getUrl(hash: String, type: BlockListType): Url?
    suspend fun getDomain(hash: String, type: BlockListType): Domain?
    suspend fun getTabId(tabId: String): TabId?
    suspend fun deleteTabId(tabId: TabId)
    suspend fun saveUrl(hash: String, blocked: Boolean, type: BlockListType): Any
    suspend fun saveDomain(hash: String, blocked: Boolean, type: BlockListType): Any
    suspend fun saveTabId(tabId: TabId)
    suspend fun runMaintenance()
}