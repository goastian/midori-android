package org.midorinext.android.contentBlocker

import android.net.InetAddresses
import android.net.Uri
import android.os.Build
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.midorinext.android.contentBlocker.cacheDb.ContentBlockerCacheRepository
import org.midorinext.android.contentBlocker.cacheDb.TabId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.ktx.android.net.hostWithoutCommonPrefixes
import java.security.MessageDigest
import androidx.core.net.toUri

class JuniorContentBlockerState(
    private val store: BrowserStore,
    private val contentBlockerService: ContentBlockerService,
    private val contentBlockerCacheRepository: ContentBlockerCacheRepository
) : ContentBlockerState() {
    private val scope = MainScope()
    private var job: Job? = null

    override var status by mutableStateOf(Status.ALLOWED)
    override var blockReason by mutableStateOf(BlockReason.NONE)
    private val statusMap = mutableStateMapOf<String, Pair<Status, BlockReason>>()

    init {
        scope.launch {
            contentBlockerCacheRepository.runMaintenance()
        }

        scope.launch {
            store.flow().map { it.restoreComplete }
                .distinctUntilChanged()
                .onEach {
                    if (it) {
                        store.state.tabs.forEach { tab ->
                            val cachedTabId = contentBlockerCacheRepository.getTabId(tab.id)
                            cachedTabId?.let { cachedTab ->
                                if (cachedTab.url == tab.content.url) {
                                    val blockStatus = if (cachedTab.blocked) Status.BLOCKED else Status.ALLOWED
                                    statusMap[tab.id] = Pair(blockStatus, cachedTab.reason)
                                } else {
                                    contentBlockerCacheRepository.deleteTabId(cachedTab)
                                }
                            }
                        }
                    }
                }
                .collect()
        }

        scope.launch {
            store.flow()
                .mapNotNull { state -> state.selectedTabId }
                .onEach {
                    val p = statusMap[it]
                    status = p?.first ?: Status.ALLOWED
                    blockReason = p?.second ?: BlockReason.NONE
                }
                .collect()
        }
    }

    override fun getStatusForTab(tabId: String): Status {
        return statusMap[tabId]?.first ?: Status.ALLOWED
    }

    override fun getBlockReasonForTab(tabId: String): BlockReason {
        return statusMap[tabId]?.second ?: BlockReason.NONE
    }

    /* override fun isTabBlocked(tabId: String?) : Boolean {
        return if (tabId == null) {
            status != Status.ALLOWED || statusMap[store.state.selectedTabId]?.first == Status.BLOCKED
        } else {
            statusMap[tabId]?.first == Status.BLOCKED
        }
    } */

    private suspend fun block(reason: BlockReason) {
        status = Status.BLOCKED
        blockReason = reason
        store.state.selectedTab?.let { tab ->
            statusMap[tab.id] = Pair(Status.BLOCKED, reason)
            contentBlockerCacheRepository.saveTabId(TabId(tab.id, true, reason, tab.content.url))
        }
    }

    private suspend fun allow() {
        status = Status.ALLOWED
        blockReason = BlockReason.NONE
        store.state.selectedTab?.let { tab ->
            statusMap[tab.id] = Pair(Status.ALLOWED, BlockReason.NONE)
            contentBlockerCacheRepository.saveTabId(TabId(tab.id, false, BlockReason.NONE, tab.content.url))
        }
    }

    override fun onMidori() = scope.launch {
        allow()
    }

    override fun check(url: String) = scope.launch {
        val uri = url.toUri()
        uri.hostWithoutCommonPrefixes?.let { host ->
            if (isIP(host)) {
                block(BlockReason.IP)
                return@launch
            }

            if (isExternalSearchEngine(host)) {
                block(BlockReason.SEARCH_ENGINE)
                return@launch
            }

            status = Status.CHECKING
            job = scope.launch(Dispatchers.IO) {
                checkAsync(uri)
            }
        }
    }

    override fun cancel() {
        job?.cancel()
        job = null
        if (status == Status.CHECKING) {
            status = Status.ALLOWED
        }
    }

    private fun isIP(s: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return InetAddresses.isNumericAddress(s)
        } else {
            @Suppress("DEPRECATION")
            return Patterns.IP_ADDRESS.matcher(s).matches()
        }
    }

    private fun isExternalSearchEngine(host: String): Boolean {
        if (searchEngineWhitelist.contains(host)) {
            return false
        }
        return blockedSearchEnginesRegex.any {
            host.matches(it)
        }
    }

    private suspend fun checkAsync(uri: Uri) = withContext(Dispatchers.IO) {
        val host = uri.hostWithoutCommonPrefixes
        if (host != null) {
            checkDomain(host)

            if (status == Status.CHECKING) {
                val path = if (uri.path?.isNotEmpty() == true && uri.path != "/") uri.path else null
                checkUrl(host, path)
            }

            if (status == Status.CHECKING) {
                allow()
            }
        } else {
            block(BlockReason.INVALID)
        }
    }

    private suspend fun checkDomain(host: String) {
        if (contentBlockerService.isDomainBlocked(getHash(host))) {
            block(BlockReason.DOMAIN)
        }
    }

    private suspend fun checkUrl(host: String, path: String? = null) {
        if (contentBlockerService.isUrlBLocked(getHash(host, path))) {
            block(BlockReason.URL)
        }
    }

    private fun getHash(host: String, path: String? = null): String {
        val reversedHost = host.split(".").reversed().joinToString(".")
        var fullPath = reversedHost
        path?.let { fullPath += it }
        return fullPath.md5()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.toHexString()
    }

    companion object {
        private val searchEngineWhitelist = listOf("docs.google.com", "accounts.google.com")
        private val blockedSearchEnginesRegex = arrayOf("sm.cn", "mail.ru", "translate.goog")
            .map { it.replace(".", "\\.") }
            .map { "([a-zA-Z0-9-]+\\.)?$it" }
            .map { it.toRegex() }
            .plus(
                arrayOf(
                    "google", "astiango", "bing", "yahoo", "baidu", "yandex", "duckduckgo", "sogou", "ecosia", "naver", "coccoc",
                    "petalsearch", "seznam", "so", "startpage", "daum", "ask", "exalead", "gigablast", "kelseek", "lycos",
                    "mozbot", "v9", "sukoga", "swisscows", "search.brave", "search.lilo"
                )
                    .map { it.replace(".", "\\.") }
                    .map { "([a-zA-Z0-9-]+\\.)?$it(\\.[a-zA-Z0-9]+)+" }
                    .map { it.toRegex() }
            )
    }
}