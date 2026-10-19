package org.midorinext.android.adblock

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class NativeFilterSources @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AdBlockSettings,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val metadata = context.getSharedPreferences("native_adblock_http", Context.MODE_PRIVATE)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val downloadMutex = Mutex()
    private val cacheWarmupMutex = Mutex()
    private val engineMutex = Mutex()
    private val redirectResources by lazy { context.assets.open("adblock/redirect-resources.json").bufferedReader().use { it.readText() } }
    private data class EngineSnapshot(
        val sources: List<Pair<String, NativeFilterRules>>,
        val trackerKeys: Set<String>,
        val all: RustFilterEngine,
        val trackers: RustFilterEngine,
    ) {
        fun close() {
            all.close()
            if (trackers !== all) trackers.close()
        }
    }
    private var engineSnapshot: EngineSnapshot? = null
    private val loadingCacheKeys = ConcurrentHashMap.newKeySet<String>()
    private val loadedCacheKeys = ConcurrentHashMap.newKeySet<String>()
    private val lastAttempt = ConcurrentHashMap<String, Long>()
    @Volatile private var rules: Map<String, NativeFilterRules> = emptyMap()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableFailures = MutableStateFlow<Set<String>>(emptySet())
    val failedSources = mutableFailures.asStateFlow()
    private val mutableRuleCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val ruleCounts = mutableRuleCounts.asStateFlow()
    internal val ready = CompletableDeferred<Unit>()

    init {
        scope.launch {
            if (metadata.getInt("format_version", 0) != CACHE_FORMAT_VERSION) {
                File(context.filesDir, "adblock-sources").listFiles().orEmpty()
                    .filter { it.extension == "txt" || it.extension == "tmp" }.forEach { it.delete() }
                metadata.edit().putInt("format_version", CACHE_FORMAT_VERSION).apply()
            }
            OfficialFilterCatalog.all.filter { it.fallbackAsset != null }.forEach { source ->
                runCatching {
                    val startedAt = android.os.SystemClock.elapsedRealtime()
                    val prefix = source.fallbackAsset!!
                    val compiled = runCatching {
                        context.assets.open("adblock/compiled/${source.id}.json").bufferedReader().use { NativeFilterRules.fromCompiled(it.readText()) }
                    }.getOrNull()
                    val parsed = compiled ?: context.assets.open("adblock/$prefix.txt").bufferedReader().use {
                        parseHostSource(it.readText(), trusted = true)
                    }
                    putRules(source.id, parsed)
                    if (org.midorinext.android.BuildConfig.DEBUG) {
                        Log.d(TAG, "Loaded bundled ${source.id} in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms")
                    }
                }.onFailure { Log.e(TAG, "Could not load bundled ${source.id}", it) }
            }
            enabledSources(settings.current, includeAds = true).forEach { (key, url, _) ->
                if (cacheFile(url).exists()) loadCacheIfFast(key, url)
            }
            settings.state.map { it.networkPolicyConfiguration() }.distinctUntilChanged().collectLatest {
                val config = settings.current
                if (config.hasBlockingSites()) {
                    enabledSources(config).forEach { (key, url, updatedAt) ->
                        if (key !in loadedCacheKeys && cacheFile(url).exists()) loadCacheIfFast(key, url)
                        if (!cacheFile(url).exists() || System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS) {
                            refreshStaleAsync(key)
                        }
                    }
                }
                prepareEngines(config)
                mutableRevision.update { it + 1 }
                ready.complete(Unit)
            }
        }
    }

    private fun activeRules(config: AdBlockConfiguration): List<Pair<String, NativeFilterRules>> {
        val snapshot = rules
        val keys = OfficialFilterCatalog.all.filter { it.id in config.officialEnabled }.map { it.id } +
            config.sources.filter { it.enabled }.map { it.url }
        return keys.mapNotNull { key -> snapshot[key]?.let { key to it } }
    }

    private fun trackerKeys(config: AdBlockConfiguration): Set<String> =
        OfficialFilterCatalog.all.filter { it.tracker }.mapTo(hashSetOf()) { it.id } +
            config.sources.filter { it.tracker }.map { it.url }

    internal suspend fun prepareEngines(config: AdBlockConfiguration) = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            val active = activeRules(config)
            val trackerKeys = trackerKeys(config)
            if (synchronized(this@NativeFilterSources) { engineSnapshot?.sources == active && engineSnapshot?.trackerKeys == trackerKeys }) return@withLock
            val trackerSources = active.filter { it.first in trackerKeys }
            val all = loadEngine(active.map { it.second.engineSource })
            val trackers = try {
                if (trackerSources == active) all else loadEngine(trackerSources.map { it.second.engineSource })
            } catch (error: Throwable) { all.close(); throw error }
            synchronized(this@NativeFilterSources) {
                val previous = engineSnapshot
                engineSnapshot = EngineSnapshot(active, trackerKeys, all, trackers)
                previous?.close()
            }
        }
    }

    private fun loadEngine(lists: List<String>): RustFilterEngine {
        val fingerprint = RustFilterEngine.fingerprint(lists, redirectResources)
        val directory = File(context.cacheDir, "adblock-rust").apply { mkdirs() }
        val file = File(directory, "$fingerprint.dat")
        if (file.exists()) runCatching { RustFilterEngine.restore(file.readBytes(), redirectResources) }
            .onSuccess { return it }.onFailure { file.delete() }
        runCatching {
            context.assets.open("adblock/compiled/rust/$fingerprint.dat").use { RustFilterEngine.restore(it.readBytes(), redirectResources) }
        }.onSuccess { return it }
        val engine = RustFilterEngine.compile(lists, redirectResources)
        runCatching {
            val temporary = File(file.path + ".tmp")
            temporary.writeBytes(engine.serialize())
            require(temporary.renameTo(file))
            directory.listFiles().orEmpty().filter { it != file }.sortedByDescending { it.lastModified() }
                .drop(5).forEach { it.delete() }
        }.onFailure { Log.w(TAG, "Could not cache Rust filters", it) }
        return engine
    }

    @Synchronized
    internal fun networkDecision(config: AdBlockConfiguration, level: BlockingLevel, url: String, source: String,
        type: String, method: String = "GET", document: String = source): JSONObject {
        val snapshot = engineSnapshot
        if (level == BlockingLevel.OFF || snapshot == null || snapshot.sources != activeRules(config) || snapshot.trackerKeys != trackerKeys(config)) return JSONObject().put("cancel", false)
        return (if (level == BlockingLevel.TRACKERS) snapshot.trackers else snapshot.all).query(url, source, type, method, document)
    }

    @Synchronized
    internal fun nativeCosmetics(config: AdBlockConfiguration, url: String, classes: List<String>, ids: List<String>): JSONObject? {
        val snapshot = engineSnapshot ?: return null
        if (snapshot.sources != activeRules(config)) return null
        return snapshot.all.cosmetics(url, classes, ids)
    }

    internal fun decision(
        config: AdBlockConfiguration,
        level: BlockingLevel,
        url: String,
        siteHost: String?,
        type: String = "subdocument",
        thirdParty: Boolean = true,
        documentUrl: String? = null,
    ): RuleDecision {
        if (level == BlockingLevel.OFF) return RuleDecision.NONE
        synchronized(this) {
            if (engineSnapshot?.sources == activeRules(config)) {
                val result = networkDecision(config, level, url, siteHost?.let { "https://$it/" }.orEmpty(), type,
                    document = documentUrl.orEmpty())
                return if (result.optBoolean("cancel")) RuleDecision.BLOCK else RuleDecision.NONE
            }
        }
        val snapshot = rules
        var blocked = false
        var allowed = false
        var importantBlock = false
        for (source in OfficialFilterCatalog.all) {
            if (source.id !in config.officialEnabled || (!source.tracker && level != BlockingLevel.TRACKERS_AND_ADS)) continue
            if (documentUrl != null && snapshot[source.id]?.pageException(documentUrl, "document") == true) return RuleDecision.ALLOW
            when (snapshot[source.id]?.decision(url, siteHost, type, thirdParty)) {
                RuleDecision.IMPORTANT_ALLOW -> return RuleDecision.ALLOW
                RuleDecision.IMPORTANT_BLOCK -> importantBlock = true
                RuleDecision.ALLOW -> allowed = true
                RuleDecision.BLOCK -> blocked = true
                else -> Unit
            }
        }
        for (source in config.sources) {
            if (!source.enabled || (!source.tracker && level != BlockingLevel.TRACKERS_AND_ADS)) continue
            if (documentUrl != null && snapshot[source.url]?.pageException(documentUrl, "document") == true) return RuleDecision.ALLOW
            when (snapshot[source.url]?.decision(url, siteHost, type, thirdParty)) {
                RuleDecision.IMPORTANT_ALLOW -> return RuleDecision.ALLOW
                RuleDecision.IMPORTANT_BLOCK -> importantBlock = true
                RuleDecision.ALLOW -> allowed = true
                RuleDecision.BLOCK -> blocked = true
                else -> Unit
            }
        }
        return when {
            importantBlock -> RuleDecision.BLOCK
            allowed -> RuleDecision.ALLOW
            blocked -> RuleDecision.BLOCK
            else -> RuleDecision.NONE
        }
    }

    internal fun hasAdSources(config: AdBlockConfiguration): Boolean =
        OfficialFilterCatalog.all.any { !it.tracker && it.id in config.officialEnabled && rules[it.id] != null } ||
            config.sources.any { !it.tracker && it.enabled && rules[it.url] != null }

    internal fun scriptletPolicy(config: AdBlockConfiguration): JSONObject {
        val snapshot = rules
        val active = OfficialFilterCatalog.all.filter { it.id in config.officialEnabled }
            .map { it.id to it.tracker } + config.sources.filter { it.enabled }.map { it.url to it.tracker }
        val scriptlets = JSONArray()
        val exceptions = JSONArray()
        for ((key, tracker) in active) {
            val source = snapshot[key] ?: continue
            source.scriptlets.forEach { scriptlets.put(it.toJson(tracker)) }
            source.documentExceptions(tracker).forEach(exceptions::put)
        }
        return JSONObject().put("level", config.level.name)
            .put("siteLevels", JSONObject(config.siteLevels.mapValues { it.value.name }))
            .put("rules", scriptlets).put("documentExceptions", exceptions)
    }

    internal fun cosmeticRules(config: AdBlockConfiguration, url: String, level: BlockingLevel = config.levelFor(url)): CosmeticSelection {
        val host = hostOf(url) ?: return CosmeticSelection(enabled = false)
        if (level != BlockingLevel.TRACKERS_AND_ADS) return CosmeticSelection(enabled = false)
        val snapshot = rules
        val keys = OfficialFilterCatalog.all.filter { !it.tracker && it.id in config.officialEnabled }.map { it.id } +
            config.sources.filter { !it.tracker && it.enabled }.map { it.url }
        val active = keys.mapNotNull(snapshot::get)
        if (active.any { it.pageException(url, "document") || it.pageException(url, "elemhide") }) {
            return CosmeticSelection(enabled = false)
        }
        return CosmeticSelection(
            rules = active.flatMap { it.cosmetics.forHost(host).toList() },
            genericEnabled = active.none { it.pageException(url, "generichide") },
        )
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
        if (!config.hasBlockingSites() && !force) return@withContext true
        enabledSources(config, includeAds = force).map { (key, url, updatedAt) ->
            if (force || !cacheFile(url).exists() || System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS) refresh(key, force)
            else true
        }.all { it }
    }

    suspend fun refresh(key: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        ready.await()
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
        compiledCacheFile(url).delete()
        loadedCacheKeys.remove(url)
        synchronized(this) { rules = rules - url }
        mutableRuleCounts.update { it - url }
        mutableFailures.update { it - url }
        mutableRevision.update { it + 1 }
    }

    private fun enabledSources(config: AdBlockConfiguration, includeAds: Boolean = false): List<Triple<String, String, Long>> =
        OfficialFilterCatalog.all.filter {
            it.id in config.officialEnabled && (it.tracker || includeAds || config.hasAdBlockingSites())
        }
            .map { Triple(it.id, it.url, config.officialUpdatedAt[it.id] ?: 0L) } +
            config.sources.filter { it.enabled && (it.tracker || includeAds || config.hasAdBlockingSites()) }
                .map { Triple(it.url, it.url, it.updatedAt) }

    private fun sourceFor(key: String): String? = OfficialFilterCatalog.get(key)?.url
        ?: settings.current.sources.firstOrNull { it.url == key }?.url

    private fun updatedAt(key: String): Long = OfficialFilterCatalog.get(key)?.let {
        settings.current.officialUpdatedAt[key] ?: 0L
    } ?: settings.current.sources.firstOrNull { it.url == key }?.updatedAt ?: 0L

    private fun loadCache(key: String, url: String) {
        val file = cacheFile(url)
        if (!file.exists()) return
        val compiled = compiledCacheFile(url)
        runCatching {
            val cached = if (compiled.exists()) runCatching { NativeFilterRules.fromCompiled(compiled.readText()) }.getOrNull() else null
            cached ?: parseHostSource(file.readText(), trusted = OfficialFilterCatalog.get(key) != null)
                .also { writeCompiledCache(url, it) }
        }
            .onSuccess {
                putRules(key, it)
                loadedCacheKeys.add(key)
            }
            .onFailure {
                file.delete()
                compiled.delete()
                Log.w(TAG, "Ignoring invalid filter cache $key", it)
            }
    }

    private fun loadCacheIfFast(key: String, url: String) {
        if (compiledCacheFile(url).exists()) {
            loadCache(key, url)
            return
        }
        if (!loadingCacheKeys.add(key)) return
        scope.launch {
            try {
                ready.await()
                cacheWarmupMutex.withLock {
                    locks.getOrPut(key) { Mutex() }.withLock {
                        if (key !in loadedCacheKeys) loadCache(key, url)
                    }
                }
            } finally { loadingCacheKeys.remove(key) }
        }
    }

    private fun compiledCacheFile(url: String): File = File(cacheFile(url).path + ".json")

    private fun writeCompiledCache(url: String, parsed: NativeFilterRules) {
        runCatching {
            val destination = compiledCacheFile(url)
            val temporary = File(destination.path + ".tmp")
            temporary.writeText(parsed.compiled(contentOnly = true).toString())
            require(temporary.renameTo(destination))
        }.onFailure { Log.w(TAG, "Could not save compiled filter cache", it) }
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
                    val parsed = parseHostSource(body, trusted = OfficialFilterCatalog.get(key) != null)
                    val temporary = File(cache.parentFile, "${cache.name}.tmp")
                    temporary.writeText(body)
                    require(temporary.renameTo(cache))
                    writeCompiledCache(sourceUrl, parsed)
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
        rules = rules + (key to parsed.contentOnly())
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
        const val CACHE_FORMAT_VERSION = 3
        const val MAX_SOURCE_BYTES = 20_000_000L
        const val UPDATE_INTERVAL_MS = 4L * 24 * 60 * 60 * 1000
        const val RETRY_INTERVAL_MS = 60L * 60 * 1000
    }
}
