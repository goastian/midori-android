package org.midorinext.android.extensions

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonsProvider
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * An [AddonsProvider] that fetches recommended Android extensions from the
 * AMO Search API (v5) instead of the collections API. This returns a much
 * richer set of recommended and reviewed extensions.
 */
class AMOSearchAddonsProvider(
    private val context: Context,
    private val client: Client,
    private val serverURL: String = DEFAULT_SERVER_URL,
    private val maxCacheAgeInMinutes: Long = 120,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AddonsProvider {

    override suspend fun getFeaturedAddons(
        allowCache: Boolean,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): List<Addon> = withContext(ioDispatcher) {
        if (allowCache && !cacheExpired(language)) {
            val cached = readFromDiskCache(language)
            if (cached != null) return@withContext cached
        }

        val lang = language ?: Locale.getDefault().language
        val url = "$serverURL/$API_VERSION/addons/search/" +
            "?app=android" +
            "&promoted=recommended" +
            "&type=extension" +
            "&sort=users" +
            "&page_size=$pageSize" +
            "&lang=$lang"

        try {
            val response = client.fetch(
                Request(
                    url = url,
                    readTimeout = Pair(
                        readTimeoutInSeconds ?: DEFAULT_READ_TIMEOUT_IN_SECONDS,
                        TimeUnit.SECONDS
                    ),
                    conservative = true,
                )
            )

            response.use { resp ->
                if (!resp.isSuccess) {
                    throw java.io.IOException("Failed to fetch add-ons: HTTP ${resp.status}")
                }
                val body = resp.body.string(Charsets.UTF_8)
                val addons = parseSearchResults(body, lang)

                if (maxCacheAgeInMinutes > 0) {
                    writeToDiskCache(body, language)
                }

                return@withContext addons
            }
        } catch (e: Exception) {
            if (allowCache) {
                // Fallback to stale cache when network is unavailable/slow.
                readFromDiskCache(language)?.let { return@withContext it }
            }
            throw e
        }
    }

    override suspend fun getAddonByID(
        id: String,
        readTimeoutInSeconds: Long?,
        language: String?,
    ): Addon? = withContext(ioDispatcher) {
        val lang = language ?: Locale.getDefault().language
        val encodedId = URLEncoder.encode(id, Charsets.UTF_8.name())
        val url = "$serverURL/$API_VERSION/addons/search/" +
            "?app=android" +
            "&type=extension" +
            "&q=$encodedId" +
            "&page_size=1" +
            "&lang=$lang"

        try {
            val response = client.fetch(
                Request(
                    url = url,
                    readTimeout = Pair(
                        readTimeoutInSeconds ?: DEFAULT_READ_TIMEOUT_IN_SECONDS,
                        TimeUnit.SECONDS
                    ),
                    conservative = true,
                )
            )

            response.use { resp ->
                if (!resp.isSuccess) {
                    return@withContext null
                }
                parseSearchResults(resp.body.string(Charsets.UTF_8), lang).firstOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSearchResults(json: String, language: String?): List<Addon> {
        val root = JSONObject(json)
        val results = root.getJSONArray("results")
        val safeLang = language?.lowercase(Locale.getDefault())
        return (0 until results.length()).mapNotNull { i ->
            try {
                results.getJSONObject(i).toAddon(safeLang)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── JSON → Addon mapping ────────────────────────────────────────────

    private fun JSONObject.toAddon(language: String?): Addon {
        val summary = getSafeTranslations("summary", language)
        val isLangInTranslations = summary.containsKey(language)
        return Addon(
            id = optString("guid", ""),
            author = getAuthor(),
            createdAt = optString("created", ""),
            updatedAt = getCurrentVersionCreated(),
            downloadUrl = getDownloadUrl(),
            version = getCurrentVersion(),
            permissions = getPermissions(),
            requiredDataCollectionPermissions = getRequiredDataCollectionPermissions(),
            optionalDataCollectionPermissions = getOptionalDataCollectionPermissions(),
            translatableName = getSafeTranslations("name", language),
            translatableDescription = getSafeTranslations("description", language),
            translatableSummary = summary,
            iconUrl = optString("icon_url", ""),
            homepageUrl = optString("url", ""),
            rating = getRating(),
            ratingUrl = optString("ratings_url", ""),
            detailUrl = optString("url", ""),
            defaultLocale = (
                if (!language.isNullOrEmpty() && isLangInTranslations) language
                else optString("default_locale", Addon.DEFAULT_LOCALE).ifEmpty { Addon.DEFAULT_LOCALE }
            ).lowercase(Locale.ROOT),
        )
    }

    private fun JSONObject.getAuthor(): Addon.Author? {
        val authors = optJSONArray("authors") ?: return null
        if (authors.length() == 0) return null
        val a = authors.getJSONObject(0)
        return Addon.Author(
            name = a.optString("name", ""),
            url = a.optString("url", ""),
        )
    }

    private fun JSONObject.getRating(): Addon.Rating? {
        val ratings = optJSONObject("ratings") ?: return null
        val avg = ratings.optDouble("average", 0.0).toFloat()
        val reviews = ratings.optInt("text_count", 0)
        return if (avg > 0) Addon.Rating(average = avg, reviews = reviews) else null
    }

    private fun JSONObject.getCurrentVersion(): String {
        return optJSONObject("current_version")?.optString("version", "") ?: ""
    }

    private fun JSONObject.getCurrentVersionCreated(): String {
        val file = optJSONObject("current_version")?.optJSONObject("file")
        return file?.optString("created", "") ?: ""
    }

    private fun JSONObject.getDownloadUrl(): String {
        val file = optJSONObject("current_version")?.optJSONObject("file")
        return file?.optString("url", "") ?: ""
    }

    private fun JSONObject.getPermissions(): List<String> {
        val file = optJSONObject("current_version")?.optJSONObject("file") ?: return emptyList()
        val perms = file.optJSONArray("permissions") ?: return emptyList()
        return (0 until perms.length()).map { perms.getString(it) }
    }

    private fun JSONObject.getRequiredDataCollectionPermissions(): List<String> {
        val file = optJSONObject("current_version")?.optJSONObject("file") ?: return emptyList()
        val permissions = file.optJSONArray("data_collection_permissions") ?: return emptyList()
        return (0 until permissions.length())
            .map { permissions.getString(it) }
            .filterNot { it == "none" }
    }

    private fun JSONObject.getOptionalDataCollectionPermissions(): List<Addon.Permission> {
        val file = optJSONObject("current_version")?.optJSONObject("file") ?: return emptyList()
        val permissions = file.optJSONArray("optional_data_collection_permissions") ?: return emptyList()
        return (0 until permissions.length()).map {
            Addon.Permission(name = permissions.getString(it), granted = false)
        }
    }

    private fun JSONObject.getSafeTranslations(key: String, language: String?): Map<String, String> {
        val value = opt(key) ?: return emptyMap()
        return when (value) {
            is JSONObject -> {
                val map = mutableMapOf<String, String>()
                value.keys().forEach { k ->
                    // Skip the "_default" meta-key returned by AMO
                    if (k == "_default") return@forEach
                    // Skip JSON null values (optString returns "null" for them)
                    if (value.isNull(k)) return@forEach
                    val v = value.optString(k, "")
                    if (v.isNotBlank()) {
                        map[k.lowercase(Locale.ROOT)] = v
                    }
                }
                // If language specified, keep only that + en-us as fallback
                if (!language.isNullOrEmpty()) {
                    val filtered = mutableMapOf<String, String>()
                    map[language]?.let { filtered[language] = it }
                    map[Addon.DEFAULT_LOCALE]?.let { filtered[Addon.DEFAULT_LOCALE] = it }
                    if (filtered.isEmpty()) map else filtered
                } else {
                    map
                }
            }
            is String -> if (value.isNotBlank()) mapOf(Addon.DEFAULT_LOCALE to value) else emptyMap()
            else -> emptyMap()
        }
    }

    // ── Disk cache ──────────────────────────────────────────────────────

    private fun getCacheFile(language: String?): File {
        val lang = language?.replace("/", "_") ?: "default"
        return File(context.filesDir, "amo_search_cache_$lang.json")
    }

    private fun cacheExpired(language: String?): Boolean {
        if (maxCacheAgeInMinutes <= 0) return true
        val file = getCacheFile(language)
        if (!file.exists()) return true
        val ageMs = System.currentTimeMillis() - file.lastModified()
        return ageMs > maxCacheAgeInMinutes * 60 * 1000
    }

    private fun readFromDiskCache(language: String?): List<Addon>? {
        return try {
            val file = getCacheFile(language)
            if (!file.exists()) return null
            val json = file.readText(Charsets.UTF_8)
            parseSearchResults(json, language)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeToDiskCache(json: String, language: String?) {
        try {
            getCacheFile(language).writeText(json, Charsets.UTF_8)
        } catch (_: Exception) {
            // Silently ignore cache write failures
        }
    }

    companion object {
        private const val API_VERSION = "api/v5"
        private const val DEFAULT_SERVER_URL = "https://services.addons.mozilla.org"
        private const val DEFAULT_READ_TIMEOUT_IN_SECONDS = 20L
        private const val DEFAULT_PAGE_SIZE = 50
    }
}
