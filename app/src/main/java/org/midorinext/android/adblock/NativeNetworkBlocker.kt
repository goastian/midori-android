package org.midorinext.android.adblock

import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeNetworkBlocker @Inject constructor(
    private val settings: AdBlockSettings,
    private val sources: NativeFilterSources,
) {

    internal suspend fun blocks(message: JSONObject): Boolean = withTimeoutOrNull(1_200L) {
        decision(message).optBoolean("cancel")
    } ?: false

    internal suspend fun decision(message: JSONObject): JSONObject = withTimeoutOrNull(1_200L) {
        val allowed = JSONObject().put("cancel", false)
        val type = resourceType(message.optString("resourceType"))
        if (type !in RESOURCE_TYPES) return@withTimeoutOrNull allowed
        val url = message.optString("url").takeIf { it.length <= 16_384 } ?: return@withTimeoutOrNull allowed
        val siteUrl = message.optString("siteUrl").takeIf { it.length <= 16_384 } ?: return@withTimeoutOrNull allowed
        if (hostOf(siteUrl) == null) return@withTimeoutOrNull allowed
        val origin = message.optString("originUrl").takeIf { hostOf(it) != null } ?: siteUrl
        val config = settings.current
        val level = config.levelFor(siteUrl)
        if (level == BlockingLevel.OFF) return@withTimeoutOrNull allowed
        sources.ready.await()
        sources.networkDecision(config, level, url, origin, type, message.optString("method", "GET"), siteUrl)
    } ?: JSONObject().put("cancel", false)

    private companion object {
        val RESOURCE_TYPES = setOf("script", "image", "stylesheet", "font", "media", "object",
            "xmlhttprequest", "ping", "websocket", "other", "subdocument", "document")
    }
}
