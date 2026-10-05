package org.midorinext.android.adblock

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NativeFilterSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AdBlockSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadata = context.getSharedPreferences("native_adblock_http", Context.MODE_PRIVATE)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val downloadMutex = Mutex()
    private val loadedCacheKeys = ConcurrentHashMap.newKeySet<String>()
    private val lastAttempt = ConcurrentHashMap<String, Long>()
    @Volatile private var rules: Map<String, NativeFilterRules> = emptyMap()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableFailures = MutableStateFlow<Set<String>>(emptySet())
    val failedSources = mutableFailures.asStateFlow()
    private val mutableRuleCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ruleCounts = mutableRuleCounts.asStateFlow()

    init {
        scope.launch {
            OfficialFilterCatalog.all.filter { it.fallbackAsset != null }.forEach { source ->
                runCatching {
                    val prefix = source.fallbackAsset!!
                    val blocked = context.assets.open("adblock/$prefix.txt").bufferedReader().use { it.readText() }
                    val allowed = context.assets.open("adblock/${prefix}_exceptions.txt").bufferedReader().use { it.readText() }
                    putRules(source.id, NativeFilterRules.bundled(blocked, allowed))
                }.onFailure { Log.e(TAG, "Could not load bundled ${source.id}", it) }
            }
            settings.state.collectLatest { config ->
                if (config.level == BlockingLevel.OFF) return@collectLatest
                enabledSources(config).forEach { (key, url, updatedAt) ->
                    if (key !in loadedCacheKeys && cacheFile(url).exists()) loadCache(key, url)
                    if (System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS ||
                        (!cacheFile(url).exists() && OfficialFilterCatalog.get(key)?.fallbackAsset == null)
                    ) {
                        refreshStaleAsync(key)
                    }
                }
                mutableRevision.update { it + 1 }
            }
        }
    }

    internal fun decision(config: AdBlockConfiguration, level: BlockingLevel, url: String, siteHost: String?): RuleDecision {
        if (level == BlockingLevel.OFF) return RuleDecision.NONE
        val snapshot = rules
        var blocked = false
        for (source in OfficialFilterCatalog.all) {
            if (source.id !in config.officialEnabled || (!source.tracker && level != BlockingLevel.TRACKERS_AND_ADS)) continue
            when (snapshot[source.id]?.decision(url, siteHost)) {
                RuleDecision.ALLOW -> return RuleDecision.ALLOW
                RuleDecision.BLOCK -> blocked = true
                else -> Unit
            }
        }
        for (source in config.sources) {
            if (!source.enabled || (!source.tracker && level != BlockingLevel.TRACKERS_AND_ADS)) continue
            when (snapshot[source.url]?.decision(url, siteHost)) {
                RuleDecision.ALLOW -> return RuleDecision.ALLOW
                RuleDecision.BLOCK -> blocked = true
                else -> Unit
            }
        }
        return if (blocked) RuleDecision.BLOCK else RuleDecision.NONE
    }

    fun refreshAsync(key: String) {
        scope.launch { refresh(key, force = true) }
    }

    private fun refreshStaleAsync(key: String) {
        val now = System.currentTimeMillis()
        if (now - (lastAttempt[key] ?: 0L) < RETRY_INTERVAL_MS) return
        lastAttempt[key] = now
        scope.launch { refresh(key) }
    }

    fun refreshAllAsync() {
        scope.launch { updateEnabled(force = true) }
    }

    suspend fun updateEnabled(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val config = settings.current
        if (config.level == BlockingLevel.OFF && !force) return@withContext true
        enabledSources(config, includeAds = force).map { (key, url, updatedAt) ->
            if (force || !cacheFile(url).exists() || System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS) refresh(key, force)
            else true
        }.all { it }
    }

    suspend fun refresh(key: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val source = sourceFor(key) ?: return@withContext false
        val mutex = locks.getOrPut(key) { Mutex() }
        mutex.withLock {
            val updatedAt = updatedAt(key)
            if (!force && cacheFile(source).exists() &&
                System.currentTimeMillis() - updatedAt < UPDATE_INTERVAL_MS
            ) return@withLock true
            downloadMutex.withLock {
                runCatching { download(key, source) }.onSuccess {
                    mutableFailures.update { failures -> failures - key }
                }.onFailure { error ->
                    mutableFailures.update { failures -> failures + key }
                    Log.w(TAG, "Could not update filter $key", error)
                }.isSuccess
            }
        }
    }

    fun removeCustom(url: String) {
        settings.removeSource(url)
        cacheFile(url).delete()
        loadedCacheKeys.remove(url)
        synchronized(this) { rules = rules - url }
        mutableRuleCounts.update { it - url }
        mutableFailures.update { it - url }
        mutableRevision.update { it + 1 }
    }

    private fun enabledSources(config: AdBlockConfiguration, includeAds: Boolean = false): List<Triple<String, String, Long>> =
        OfficialFilterCatalog.all.filter {
            it.id in config.officialEnabled && (it.tracker || includeAds || config.level == BlockingLevel.TRACKERS_AND_ADS)
        }
            .map { Triple(it.id, it.url, config.officialUpdatedAt[it.id] ?: 0L) } +
            config.sources.filter { it.enabled && (it.tracker || includeAds || config.level == BlockingLevel.TRACKERS_AND_ADS) }
                .map { Triple(it.url, it.url, it.updatedAt) }

    private fun sourceFor(key: String): String? = OfficialFilterCatalog.get(key)?.url
        ?: settings.current.sources.firstOrNull { it.url == key }?.url

    private fun updatedAt(key: String): Long = OfficialFilterCatalog.get(key)?.let {
        settings.current.officialUpdatedAt[key] ?: 0L
    } ?: settings.current.sources.firstOrNull { it.url == key }?.updatedAt ?: 0L

    private fun loadCache(key: String, url: String) {
        val file = cacheFile(url)
        if (!file.exists()) return
        runCatching { parseHostSource(file.readText()) }
            .onSuccess {
                putRules(key, it)
                loadedCacheKeys.add(key)
            }
            .onFailure {
                file.delete()
                Log.w(TAG, "Ignoring invalid filter cache $key", it)
            }
    }

    private fun download(key: String, sourceUrl: String) {
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MidoriAndroid/1.0")
        val cache = cacheFile(sourceUrl)
        if (cache.exists()) {
            metadata.getString("etag_$key", null)?.let { connection.setRequestProperty("If-None-Match", it) }
            metadata.getString("modified_$key", null)?.let { connection.setRequestProperty("If-Modified-Since", it) }
        }
        try {
            require(connection.url.protocol == "https")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    require(cache.exists())
                    if (key !in loadedCacheKeys) loadCache(key, sourceUrl)
                    require(rules[key] != null)
                    markUpdated(key)
                }
                HttpURLConnection.HTTP_OK -> {
                    require(connection.url.protocol == "https")
                    require(connection.contentLengthLong <= MAX_SOURCE_BYTES)
                    val bytes = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(output.size() + count <= MAX_SOURCE_BYTES)
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val body = bytes.toString(Charsets.UTF_8)
                    val supportedText = StringBuilder()
                    val parsed = parseHostSource(body) { line ->
                        supportedText.append(line).append('\n')
                    }
                    val temporary = File(cache.parentFile, "${cache.name}.tmp")
                    temporary.writeText(supportedText.toString())
                    require(temporary.renameTo(cache))
                    synchronized(this) { putRules(key, parsed) }
                    loadedCacheKeys.add(key)
                    metadata.edit()
                        .putString("etag_$key", connection.getHeaderField("ETag"))
                        .putString("modified_$key", connection.getHeaderField("Last-Modified"))
                        .apply()
                    markUpdated(key)
                }
                else -> error("HTTP ${connection.responseCode}")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun markUpdated(key: String) {
        val now = System.currentTimeMillis()
        if (OfficialFilterCatalog.get(key) != null) settings.officialSourceUpdated(key, now)
        else settings.sourceUpdated(key, now)
    }

    @Synchronized
    private fun putRules(key: String, parsed: NativeFilterRules) {
        rules = rules + (key to parsed)
        mutableRuleCounts.update { it + (key to parsed.supportedCount) }
        mutableRevision.update { it + 1 }
    }

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.filesDir, "adblock-sources").apply { mkdirs() }
        return File(directory, "$hash.txt")
    }

    private companion object {
        const val TAG = "NativeFilterSources"
        const val MAX_SOURCE_BYTES = 20_000_000L
        const val UPDATE_INTERVAL_MS = 4L * 24 * 60 * 60 * 1000
        const val RETRY_INTERVAL_MS = 60L * 60 * 1000
    }
}
