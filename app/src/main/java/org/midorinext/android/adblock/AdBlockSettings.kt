package org.midorinext.android.adblock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class BlockingLevel { OFF, TRACKERS, TRACKERS_AND_ADS }

data class BlockingSource(
    val url: String,
    val tracker: Boolean,
    val enabled: Boolean,
    val updatedAt: Long,
)

data class AdBlockConfiguration(
    val level: BlockingLevel = BlockingLevel.TRACKERS_AND_ADS,
    val strict: Boolean = false,
    val builtInTrackers: Boolean = true,
    val builtInAds: Boolean = true,
    val siteLevels: Map<String, BlockingLevel> = emptyMap(),
    val sources: List<BlockingSource> = emptyList(),
    val officialEnabled: Set<String> = emptySet(),
    val officialUpdatedAt: Map<String, Long> = emptyMap(),
    val rejectCookieBanners: Boolean = true,
) {
    fun levelFor(url: String): BlockingLevel = hostOf(url)?.let(siteLevels::get) ?: level

    internal fun hasBlockingSites(): Boolean = level != BlockingLevel.OFF || siteLevels.values.any { it != BlockingLevel.OFF }

    internal fun hasAdBlockingSites(): Boolean = level == BlockingLevel.TRACKERS_AND_ADS ||
        BlockingLevel.TRACKERS_AND_ADS in siteLevels.values

    fun rejectsCookieBannersFor(url: String): Boolean =
        rejectCookieBanners && levelFor(url) != BlockingLevel.OFF &&
            runCatching { URI(url).scheme in setOf("http", "https") && hostOf(url) != null }.getOrDefault(false)
}

internal fun hostOf(url: String): String? = runCatching {
    URI(url).host?.lowercase(Locale.ROOT)?.takeIf { it.contains('.') }
}.getOrNull()

@Singleton
class AdBlockSettings @Inject constructor(@ApplicationContext private val context: Context) {
    private val preferences = context.getSharedPreferences("native_adblock", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    val current: AdBlockConfiguration get() = mutable.value

    init {
        if (preferences.contains("excluded_hosts") || !preferences.contains("official_sources")) {
            preferences.edit()
                .putStringSet("site_levels", current.siteLevels.map { "${it.key}|${it.value.name}" }.toSet())
                .putStringSet("official_sources", current.officialEnabled)
                .remove("excluded_hosts")
                .apply()
        }
    }

    @Synchronized
    fun setLevel(level: BlockingLevel) = update { it.copy(level = level) }

    @Synchronized
    fun setStrict(enabled: Boolean) = update { it.copy(strict = enabled) }

    @Synchronized
    fun setRejectCookieBanners(enabled: Boolean) = update { it.copy(rejectCookieBanners = enabled) }

    @Synchronized
    fun setBuiltInTrackers(enabled: Boolean) = update { it.copy(builtInTrackers = enabled) }

    @Synchronized
    fun setBuiltInAds(enabled: Boolean) = update { it.copy(builtInAds = enabled) }

    @Synchronized
    fun setOfficialEnabled(id: String, enabled: Boolean) {
        if (OfficialFilterCatalog.get(id) == null) return
        update { config ->
            config.copy(officialEnabled = if (enabled) config.officialEnabled + id else config.officialEnabled - id)
        }
    }

    @Synchronized
    fun officialSourceUpdated(id: String, timestamp: Long) {
        if (OfficialFilterCatalog.get(id) == null) return
        update { config -> config.copy(officialUpdatedAt = config.officialUpdatedAt + (id to timestamp)) }
    }

    @Synchronized
    fun setSiteLevel(host: String, level: BlockingLevel?) {
        val normalized = hostOf("https://$host") ?: return
        update { it.copy(siteLevels = it.siteLevels.toMutableMap().apply {
            if (level == null) remove(normalized) else put(normalized, level)
        }) }
    }

    @Synchronized
    fun addSource(url: String, tracker: Boolean): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return false
        if (OfficialFilterCatalog.all.any { it.url == uri.toString() }) return false
        if (current.sources.any { it.url == uri.toString() }) return false
        update { it.copy(sources = it.sources + BlockingSource(uri.toString(), tracker, true, 0L)) }
        return true
    }

    @Synchronized
    fun removeSource(url: String) = update { it.copy(sources = it.sources.filterNot { source -> source.url == url }) }

    @Synchronized
    fun setSourceEnabled(url: String, enabled: Boolean) = update { config ->
        config.copy(sources = config.sources.map { if (it.url == url) it.copy(enabled = enabled) else it })
    }

    @Synchronized
    fun sourceUpdated(url: String, timestamp: Long) = update { config ->
        config.copy(sources = config.sources.map { if (it.url == url) it.copy(updatedAt = timestamp) else it })
    }

    private fun update(transform: (AdBlockConfiguration) -> AdBlockConfiguration) {
        val next = transform(mutable.value)
        preferences.edit()
            .putString("level", next.level.name)
            .putBoolean("strict", next.strict)
            .putBoolean("reject_cookie_banners", next.rejectCookieBanners)
            .putBoolean("built_in_trackers", next.builtInTrackers)
            .putBoolean("built_in_ads", next.builtInAds)
            .putStringSet("site_levels", next.siteLevels.map { "${it.key}|${it.value.name}" }.toSet())
            .putStringSet("sources", next.sources.map { "${it.tracker}|${it.enabled}|${it.updatedAt}|${it.url}" }.toSet())
            .putStringSet("official_sources", next.officialEnabled)
            .putStringSet("official_updated", next.officialUpdatedAt.map { "${it.key}|${it.value}" }.toSet())
            .apply()
        mutable.value = next
    }

    private fun read(): AdBlockConfiguration {
        val level = preferences.getString("level", null).toLevel() ?: BlockingLevel.TRACKERS_AND_ADS
        val sites = preferences.getStringSet("site_levels", emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|', limit = 2)
            parts.getOrNull(1).toLevel()?.let { parts[0] to it }
        }.toMap().toMutableMap()
        // Preserve exceptions made with the previous native blocker.
        preferences.getStringSet("excluded_hosts", emptySet()).orEmpty().forEach { host ->
            sites.putIfAbsent(host, BlockingLevel.OFF)
        }
        val sources = preferences.getStringSet("sources", emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|', limit = 4)
            if (parts.size != 4) null else BlockingSource(
                url = parts[3], tracker = parts[0].toBoolean(), enabled = parts[1].toBoolean(),
                updatedAt = parts[2].toLongOrNull() ?: 0L,
            )
        }.sortedBy { it.url }
        val language = context.resources.configuration.locales[0]?.language ?: Locale.getDefault().language
        val officialEnabled = if (preferences.contains("official_sources")) {
            preferences.getStringSet("official_sources", emptySet()).orEmpty().toSet()
        } else {
            OfficialFilterCatalog.defaults(language).toMutableSet().apply {
                if (!preferences.getBoolean("built_in_trackers", true)) remove("easyprivacy")
                if (!preferences.getBoolean("built_in_ads", true)) remove("easylist")
            }
        }
        val officialUpdatedAt = preferences.getStringSet("official_updated", emptySet()).orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split('|', limit = 2)
                parts.getOrNull(1)?.toLongOrNull()?.let { parts[0] to it }
            }.toMap()
        return AdBlockConfiguration(
            level = level,
            strict = preferences.getBoolean("strict", false),
            rejectCookieBanners = preferences.getBoolean("reject_cookie_banners", true),
            builtInTrackers = preferences.getBoolean("built_in_trackers", true),
            builtInAds = preferences.getBoolean("built_in_ads", true),
            siteLevels = sites,
            sources = sources,
            officialEnabled = officialEnabled,
            officialUpdatedAt = officialUpdatedAt,
        )
    }

    private fun String?.toLevel(): BlockingLevel? =
        runCatching { this?.let(BlockingLevel::valueOf) }.getOrNull()
}
