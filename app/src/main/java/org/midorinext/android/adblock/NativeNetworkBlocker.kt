package org.midorinext.android.adblock

import android.content.Context
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.lib.publicsuffixlist.PublicSuffixList
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeNetworkBlocker @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: AdBlockSettings,
    private val sources: NativeFilterSources,
) {
    private val suffixes = PublicSuffixList(context, Dispatchers.Default)
    private val domains = LruCache<String, String>(512)

    internal suspend fun blocks(message: JSONObject): Boolean = withTimeoutOrNull(1_200L) {
        val type = resourceType(message.optString("resourceType"))
        if (type !in RESOURCE_TYPES) return@withTimeoutOrNull false
        val url = message.optString("url").takeIf { it.length <= 16_384 } ?: return@withTimeoutOrNull false
        val siteUrl = message.optString("siteUrl").takeIf { it.length <= 16_384 } ?: return@withTimeoutOrNull false
        val siteHost = hostOf(siteUrl) ?: return@withTimeoutOrNull false
        val targetHost = hostOf(url) ?: return@withTimeoutOrNull false
        val originHost = hostOf(message.optString("originUrl")) ?: siteHost
        val config = settings.current
        val level = config.levelFor(siteUrl)
        if (level == BlockingLevel.OFF) return@withTimeoutOrNull false
        sources.ready.await()
        val thirdParty = domain(targetHost) != domain(originHost)
        sources.decision(config, level, url, originHost, type, thirdParty) == RuleDecision.BLOCK
    } ?: false

    private suspend fun domain(host: String): String {
        domains.get(host)?.let { return it }
        return (suffixes.getPublicSuffixPlusOne(host).await() ?: host).also { domains.put(host, it) }
    }

    private companion object {
        val RESOURCE_TYPES = setOf("script", "image", "stylesheet", "font", "media", "object",
            "xmlhttprequest", "ping", "websocket", "other", "subdocument")
    }
}
