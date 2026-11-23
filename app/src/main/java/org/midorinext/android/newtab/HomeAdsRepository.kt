package org.midorinext.android.newtab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import org.midorinext.android.BuildConfig

@Singleton
class HomeAdsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientProvider: dagger.Lazy<Client>,
) {
    private val requests = Semaphore(3)
    private val telemetryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    val deviceType: String get() = when {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PC) -> "desktop"
        context.resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
        else -> "mobile"
    }
    private val origin = homeAdsOrigin(BuildConfig.ASTIAN_ADS_URL, BuildConfig.DEBUG)
    private val cacheStorage by lazy { context.getSharedPreferences("home_ads", Context.MODE_PRIVATE) }
    private val creativeFields = listOf("ad_id", "format_id", "placement", "channel", "interaction_contract", "bookmark_key",
        "icon_url", "image_url", "title", "description", "cta", "disclosure_required", "sponsor_label", "locale")
    private val visitorId by lazy {
        val storage = context.getSharedPreferences("home_ads", Context.MODE_PRIVATE)
        val saved = storage.getString("visitor_id", null)
        val valid = saved?.takeIf { runCatching { UUID.fromString(it).toString() == it }.getOrDefault(false) }
        valid ?: UUID.randomUUID().toString().also { storage.edit().putString("visitor_id", it).apply() }
    }

    fun cachedCatalog(locale: Locale): List<HomeAdsBookmark>? = storedJson(cacheKey("catalog", locale))?.let {
        runCatching { parseHomeAdsCatalog(it, origin) }.getOrNull()
    }

    fun cachedContents(locale: Locale, catalog: List<HomeAdsBookmark>): Map<String, HomeAdContent> = catalog.associate { entry ->
        val creative = storedJson(cacheKey("creative", locale, entry.key))
        val label = creative?.takeIf { it.optBoolean("disclosure_required") }
            ?.optString("sponsor_label")?.takeIf { it.isNotBlank() && it.length <= 80 }
        entry.key to HomeAdContent(entry.title, entry.iconUrl, label)
    }

    suspend fun catalog(locale: Locale): List<HomeAdsBookmark> {
        val key = cacheKey("catalog", locale)
        val stored = storedJson(key)
        val cached = stored?.let { runCatching { parseHomeAdsCatalog(it, origin) }.getOrNull() }
        val etag = cacheStorage.getString("$key:etag", null)?.takeIf {
            cached != null && it.length <= 160 && '\r' !in it && '\n' !in it
        }
        val response = try {
            request("/newtab/catalog", query = context(locale), extraHeaders = etag?.let { mapOf("If-None-Match" to it) }.orEmpty())
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { return cached ?: throw error }
        if (response.status == 304) return requireNotNull(cached)
        val json = requireNotNull(response.json)
        val catalog = parseHomeAdsCatalog(json, origin)
        cacheStorage.edit().putString(key, json.toString()).putString("$key:etag", response.etag).apply()
        return catalog
    }

    suspend fun decision(key: String, locale: Locale): HomeAdDecision? = decision(key, locale, true)

    private suspend fun decision(key: String, locale: Locale, allowCached: Boolean): HomeAdDecision? {
        require(homeAdsKeyPattern.matches(key))
        val cacheKey = cacheKey("creative", locale, key)
        val cached = if (allowCached) storedJson(cacheKey)?.takeIf {
            it.optString("creative_contract") == "ads-creative-v1" && it.optLong("ad_id") > 0 &&
                Regex("[a-f0-9]{64}").matches(it.optString("creative_version"))
        } else null
        val query = context(locale) + ("bookmark_key" to key)
        var response = if (cached == null) request("/newtab", query = query) else request("/newtab/lease",
            body = JSONObject(query).put("cached_ad_id", cached.getLong("ad_id"))
                .put("creative_version", cached.getString("creative_version")), readBody = true)
        if (cached != null && response.status in setOf(404, 405)) {
            cacheStorage.edit().remove(cacheKey).apply()
            response = request("/newtab", query = query)
        }
        if (response.status == 204 || response.status == 404) {
            cacheStorage.edit().remove(cacheKey).apply()
            return null
        }
        val payload = requireNotNull(response.json)
        try {
            val json = if (payload.optBoolean("creative_unchanged")) {
                require(cached != null && cached.getString("creative_version") == payload.getString("creative_version") &&
                    cached.getLong("ad_id") == payload.getLong("ad_id"))
                JSONObject().also { merged ->
                    creativeFields.filter(cached::has).forEach { merged.put(it, cached.get(it)) }
                    payload.keys().forEach { merged.put(it, payload.get(it)) }
                }
            } else payload
            val decision = parseHomeAdDecision(json, key, origin = origin)
            if (json.optString("creative_contract") == "ads-creative-v1" && Regex("[a-f0-9]{64}").matches(json.optString("creative_version"))) {
                val creative = JSONObject().put("creative_contract", "ads-creative-v1").put("creative_version", json.getString("creative_version"))
                creativeFields.filter(json::has).forEach { creative.put(it, json.get(it)) }
                cacheStorage.edit().putString(cacheKey, creative.toString()).apply()
            }
            return decision
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            cacheStorage.edit().remove(cacheKey).apply()
            if (cached != null) return decision(key, locale, false)
            throw error
        }
    }

    private fun cacheKey(kind: String, locale: Locale, bookmark: String = "") =
        "$kind:v1:$origin:$visitorId:${homeAdsLanguage(locale)}:$deviceType:$bookmark"

    private fun storedJson(key: String): JSONObject? = runCatching {
        cacheStorage.getString(key, null)?.let(::JSONObject)
    }.getOrNull()

    suspend fun render(ad: HomeAdDecision): Boolean = event("/render", ad)

    suspend fun impression(ad: HomeAdDecision): Boolean {
        repeat(2) { attempt ->
            if (event("/impression", ad)) return true
            if (attempt == 0 && ad.isActive()) delay(200)
        }
        return false
    }

    suspend fun dismissed(ad: HomeAdDecision): Boolean = event("/client-event", ad, JSONObject()
        .put("event_id", UUID.randomUUID().toString()).put("event_type", "ad_dismissed"))

    private suspend fun event(path: String, ad: HomeAdDecision, body: JSONObject = JSONObject()): Boolean {
        if (!ad.isActive()) return false
        val (status, _) = request(path, body = body.put("impression_token", ad.impressionToken))
        return status in 200..299
    }

    fun finishExposure(exposure: HomeAdExposure) {
        val duration = exposure.visibleMillis()
        val body = JSONObject().put("event_id", exposure.id).put("event_type", "exposure_finished")
            .put("impression_token", exposure.ad.impressionToken).put("surface", "android_home")
            .put("elapsed_ms", duration).put("visible_ms", duration).put("viewable", duration >= 1000)
            .put("end_reason", exposure.endReason)
        exposure.position?.let { body.put("position", it.coerceIn(0, 127)) }
        sendInteraction(body)
    }

    fun bookmarkAction(key: String, event: String, ad: HomeAdDecision? = null) {
        if (!homeAdsKeyPattern.matches(key) || event !in setOf("bookmark_added", "bookmark_changed", "bookmark_removed", "bookmark_moved")) return
        val body = JSONObject().put("event_id", UUID.randomUUID().toString()).put("event_type", event)
            .put("visitor_id", visitorId).put("bookmark_key", key).put("surface", "android_home").put("device_type", deviceType)
        if (ad?.isActive() == true && ad.bookmarkKey == key) body.put("impression_token", ad.impressionToken)
        sendInteraction(body)
    }

    private fun sendInteraction(body: JSONObject) {
        telemetryScope.launch {
            try {
                repeat(2) { attempt ->
                    val (status, _) = request("/interaction", body = body)
                    if (status in 200..299 || status in 400..499 && status != 429) return@launch
                    if (attempt == 0) delay(500)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }

    private fun context(locale: Locale) = buildMap {
        put("device_type", deviceType)
        put("surface", "android_home")
        put("visitor_id", visitorId)
        put("language", homeAdsLanguage(locale))
    }

    private suspend fun request(
        path: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null,
        extraHeaders: Map<String, String> = emptyMap(), readBody: Boolean = body == null,
    ): HomeAdsHttpResponse = retryNetworkRequest {
        withContext(Dispatchers.IO) {
            requests.withPermit {
                val coroutineContext = currentCoroutineContext()
                val suffix = query.entries.joinToString("&") {
                    URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
                }
                val url = "$origin/api/v1/ads$path" + if (suffix.isEmpty()) "" else "?$suffix"
                val headers = MutableHeaders().apply {
                    append("Accept", "application/json")
                    if (body != null) append("Content-Type", "application/json")
                    extraHeaders.forEach { (name, value) -> append(name, value) }
                }
                val request = Request(url, method = if (body == null) Request.Method.GET else Request.Method.POST,
                    headers = headers, body = body?.let { Request.Body.fromString(it.toString()) },
                    connectTimeout = 3L to TimeUnit.SECONDS, readTimeout = 6L to TimeUnit.SECONDS,
                    redirect = Request.Redirect.MANUAL, cookiePolicy = Request.CookiePolicy.OMIT, useCaches = false)
                try {
                    coroutineContext.ensureActive()
                    clientProvider.get().fetch(request).use { response ->
                        coroutineContext.ensureActive()
                        val status = response.status
                        val etag = response.headers["ETag"]
                        if (!readBody || status in setOf(204, 304, 404, 405)) return@withPermit HomeAdsHttpResponse(status, null, etag)
                        check(status in 200..299)
                        val text = response.body.useBufferedReader { reader ->
                            val result = StringBuilder()
                            val buffer = CharArray(4096)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = reader.read(buffer)
                                if (count < 0) break
                                check(result.length + count <= 262_144)
                                result.append(buffer, 0, count)
                            }
                            result.toString()
                        }
                        HomeAdsHttpResponse(status, JSONObject(text), etag)
                    }
                } finally {
                    request.body?.close()
                }
            }
        }
    }

    private suspend fun <T> retryNetworkRequest(request: suspend () -> T): T {
        return try { request() } catch (_: IOException) {
            delay(500)
            request()
        }
    }
}

private data class HomeAdsHttpResponse(val status: Int, val json: JSONObject?, val etag: String?)
