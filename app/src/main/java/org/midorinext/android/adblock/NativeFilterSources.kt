package org.midorinext.android.adblock

import android.app.ActivityManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
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
    private val engineMutex = Mutex()
    private val redirectResources by lazy { context.assets.open("adblock/redirect-resources.json").bufferedReader().use { it.readText() } }
    private val redirectResourcesSha256 by lazy { RustFilterEngine.sha256(redirectResources.toByteArray()) }
    private val sourcesDirectory get() = File(context.filesDir, "adblock-sources").apply { mkdirs() }
    private val refreshesInFlight = AtomicInteger()
    private val announcePending = AtomicBoolean()
    private val staleEngineFiles = ConcurrentHashMap.newKeySet<File>()
    private class EngineSnapshot(
        val sources: List<Pair<String, NativeFilterRules>>,
        val trackerKeys: Set<String>,
        val all: RustFilterEngine?,
        /** Only built while a tracker-only level is in use. */
        val trackers: RustFilterEngine?,
    ) {
        val keys = sources.map { it.first }

        fun close() {
            all?.close()
            if (trackers !== all) trackers?.close()
        }
    }
    private var engineSnapshot: EngineSnapshot? = null
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
            removeLegacyCaches()
            settings.state.map { it.networkPolicyConfiguration() }.distinctUntilChanged().collectLatest {
                val config = settings.current
                prepareEngines(config)
                mutableRevision.update { it + 1 }
                ready.complete(Unit)
                if (config.hasBlockingSites()) {
                    enabledSources(config).forEach { (key, url, updatedAt) ->
                        if (!cacheFile(url).exists() || System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS) {
                            refreshStaleAsync(key)
                        }
                    }
                }
            }
        }
    }

    private fun activeRules(config: AdBlockConfiguration): List<Pair<String, NativeFilterRules>> {
        val snapshot = rules
        val keys = if (config.hasBlockingSites()) enabledSources(config).map { it.first } else emptyList()
        return keys.mapNotNull { key -> snapshot[key]?.let { key to it } }
    }

    private fun trackerKeys(config: AdBlockConfiguration): Set<String> =
        OfficialFilterCatalog.all.filter { it.tracker }.mapTo(hashSetOf()) { it.id } +
            config.sources.filter { it.tracker }.map { it.url }

    internal suspend fun prepareEngines(config: AdBlockConfiguration) = withContext(Dispatchers.Default) {
        engineMutex.withLock {
            try {
                val needed = if (config.hasBlockingSites()) enabledSources(config) else emptyList()
                for ((key, url, _) in needed) {
                    if (rules[key] != null) continue
                    val official = OfficialFilterCatalog.get(key)
                    val downloaded = readDownloaded(url)
                    when {
                        downloaded != null -> { putRules(key, downloaded); loadedCacheKeys.add(key) }
                        official?.fallbackAsset != null -> putRules(key, loadBundled(official))
                        cacheFile(url).exists() -> loadCache(key, url)
                    }
                }
                val active = activeRules(config)
                val trackerKeys = trackerKeys(config)
                val needsAll = config.hasAdBlockingSites()
                val needsTrackers = config.level == BlockingLevel.TRACKERS || BlockingLevel.TRACKERS in config.siteLevels.values
                val current = synchronized(this@NativeFilterSources) { engineSnapshot }
                val sameRules = current?.sources == active && current.trackerKeys == trackerKeys
                if (sameRules && (current?.all != null) == needsAll && (current?.trackers != null) == needsTrackers) return@withLock
                // Keep serving the previous engine until the lists being downloaded have settled; one rebuild then covers all of them.
                if (current != null && refreshesInFlight.get() > 0 && current.keys == active.map { it.first } &&
                    current.trackerKeys == trackerKeys && (current.all != null) == needsAll && (current.trackers != null) == needsTrackers) return@withLock
                val all = if (needsAll) {
                    if (sameRules && current?.all != null) current.all else loadEngine(active.mapNotNull { it.second.engine })
                } else null
                var trackers: RustFilterEngine? = null
                try {
                    trackers = when {
                        !needsTrackers -> null
                        sameRules && current?.trackers != null -> current.trackers
                        all != null && active.all { it.first in trackerKeys } -> all
                        else -> loadEngine(active.filter { it.first in trackerKeys }.mapNotNull { it.second.engine })
                    }
                    coroutineContext.ensureActive()
                    synchronized(this@NativeFilterSources) {
                        engineSnapshot = if (needsAll || needsTrackers) EngineSnapshot(active, trackerKeys, all, trackers) else null
                        if (current?.all !== all && current?.all !== trackers) current?.all?.close()
                        if (current?.trackers !== current?.all && current?.trackers !== all && current?.trackers !== trackers) current?.trackers?.close()
                        val keys = needed.mapTo(hashSetOf()) { it.first }
                        rules = rules.filterKeys { it in keys }
                        loadedCacheKeys.retainAll(keys)
                    }
                } catch (error: Throwable) {
                    if (all !== current?.all && all !== current?.trackers) all?.close()
                    if (trackers !== all && trackers !== current?.all && trackers !== current?.trackers) trackers?.close()
                    throw error
                }
                deleteStaleEngineFiles()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.e(TAG, "Could not prepare the filter engines", error)
            }
        }
    }

    internal suspend fun close() {
        ready.cancel()
        scope.coroutineContext[Job]?.cancelAndJoin()
        engineMutex.withLock {
            synchronized(this) {
                engineSnapshot?.close()
                engineSnapshot = null
                rules = emptyMap()
                loadedCacheKeys.clear()
            }
        }
    }

    internal fun engineState(): Pair<Boolean, Boolean> = synchronized(this) {
        (engineSnapshot?.all != null) to (engineSnapshot?.trackers != null)
    }

    private fun isUnderMemoryPressure(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return ActivityManager.MemoryInfo().also(manager::getMemoryInfo).lowMemory
    }

    private fun loadEngine(lists: List<EngineList>): RustFilterEngine {
        val fingerprint = RustFilterEngine.fingerprint(lists, redirectResourcesSha256)
        val directory = File(context.cacheDir, "adblock-rust").apply { mkdirs() }
        val file = File(directory, "$ENGINE_CACHE_PREFIX$fingerprint.dat")
        if (file.exists()) runCatching { RustFilterEngine.restoreFile(file, redirectResources) }
            .onSuccess { return it }.onFailure { file.delete() }
        if (copyAsset("adblock/compiled/rust/$fingerprint.dat", file)) {
            runCatching { RustFilterEngine.restoreFile(file, redirectResources) }
                .onSuccess { return it }.onFailure { file.delete() }
        }
        val engine = RustFilterEngine.compileFiles(lists.map { it.file }, redirectResources)
        val temporary = File(file.path + ".tmp")
        runCatching {
            engine.serializeTo(temporary)
            require(temporary.renameTo(file))
        }.onFailure {
            temporary.delete()
            Log.w(TAG, "Could not cache Rust filters", it)
        }
        directory.listFiles().orEmpty().filter { it != file && it.name.endsWith(".dat") }
            .sortedByDescending { it.lastModified() }.drop(ENGINE_CACHE_ENTRIES - 1).forEach { it.delete() }
        return engine
    }

    private fun copyAsset(name: String, destination: File): Boolean {
        val temporary = File(destination.path + ".tmp")
        return try {
            context.assets.open(name).use { input -> temporary.outputStream().use { input.copyTo(it) } }
            temporary.renameTo(destination)
        } catch (_: IOException) {
            temporary.delete()
            false
        }
    }

    private fun servesConfiguration(snapshot: EngineSnapshot, config: AdBlockConfiguration) =
        snapshot.keys == activeRules(config).map { it.first } && snapshot.trackerKeys == trackerKeys(config)

    @Synchronized
    internal fun networkDecision(config: AdBlockConfiguration, level: BlockingLevel, url: String, source: String,
        type: String, method: String = "GET", document: String = source): JSONObject {
        val snapshot = engineSnapshot
        if (level == BlockingLevel.OFF || snapshot == null || !servesConfiguration(snapshot, config)) return JSONObject().put("cancel", false)
        val engine = (if (level == BlockingLevel.TRACKERS) snapshot.trackers else snapshot.all) ?: return JSONObject().put("cancel", false)
        return engine.query(url, source, type, method, document)
    }

    @Synchronized
    internal fun nativeCosmetics(config: AdBlockConfiguration, url: String, classes: List<String>, ids: List<String>): JSONObject? {
        val snapshot = engineSnapshot ?: return null
        if (snapshot.keys != activeRules(config).map { it.first }) return null
        return snapshot.all?.cosmetics(url, classes, ids)
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
            if (engineSnapshot?.keys == activeRules(config).map { it.first }) {
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

    /**
     * The scriptlet policy as JSON text. It is sent to the extension as one string: a structured message would be
     * converted into one Java object per rule, which is far more memory than the few megabytes of text.
     */
    internal fun scriptletPolicy(config: AdBlockConfiguration): String {
        val snapshot = rules
        val active = if (!config.hasBlockingSites()) emptyList() else
            OfficialFilterCatalog.all.filter { it.id in config.officialEnabled && (it.tracker || config.hasAdBlockingSites()) }
                .map { it.id to it.tracker } + config.sources.filter { it.enabled && (it.tracker || config.hasAdBlockingSites()) }.map { it.url to it.tracker }
        val out = StringBuilder(if (active.isEmpty()) 128 else 1 shl 20)
        out.append("{\"level\":").append(JSONObject.quote(config.level.name))
            .append(",\"siteLevels\":").append(JSONObject(config.siteLevels.mapValues { it.value.name }))
            .append(",\"rules\":[")
        var first = true
        for ((key, tracker) in active) {
            val source = snapshot[key] ?: continue
            for (rule in source.scriptlets) {
                if (!first) out.append(',')
                first = false
                rule.appendJson(out, tracker)
            }
        }
        out.append("],\"documentExceptions\":[")
        first = true
        for ((key, tracker) in active) {
            val source = snapshot[key] ?: continue
            for (exception in source.documentExceptions(tracker)) {
                if (!first) out.append(',')
                first = false
                out.append(exception)
            }
        }
        return out.append("]}").toString()
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
        beginRefresh()
        scope.launch { try { refreshCounted(key, force = true) } finally { endRefresh() } }
    }

    private fun refreshStaleAsync(key: String) {
        if (isUnderMemoryPressure()) return
        val now = System.currentTimeMillis()
        if (now - (lastAttempt[key] ?: 0L) < RETRY_INTERVAL_MS) return
        lastAttempt[key] = now
        beginRefresh()
        scope.launch { try { refreshCounted(key, force = false) } finally { endRefresh() } }
    }

    fun refreshAllAsync() {
        scope.launch { updateEnabled(force = true) }
    }

    suspend fun updateEnabled(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val config = settings.current
        if (!config.hasBlockingSites() && !force) return@withContext true
        beginRefresh()
        try {
            enabledSources(config, includeAds = force).map { (key, url, updatedAt) ->
                if (force || !cacheFile(url).exists() || System.currentTimeMillis() - updatedAt >= UPDATE_INTERVAL_MS) refreshCounted(key, force)
                else true
            }.all { it }
        } finally { endRefresh() }
    }

    suspend fun refresh(key: String, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        beginRefresh()
        try { refreshCounted(key, force) } finally { endRefresh() }
    }

    private fun beginRefresh() {
        refreshesInFlight.incrementAndGet()
    }

    /** Announces the lists loaded since the last announcement once nothing else is being loaded. */
    private fun endRefresh() {
        if (refreshesInFlight.decrementAndGet() == 0 && announcePending.getAndSet(false)) mutableRevision.update { it + 1 }
    }

    private suspend fun refreshCounted(key: String, force: Boolean): Boolean {
        ready.await()
        if (!force && isUnderMemoryPressure()) return false
        val source = sourceFor(key) ?: return false
        val mutex = locks.getOrPut(key) { Mutex() }
        return mutex.withLock {
            val updatedAt = updatedAt(key)
            if (!force && cacheFile(source).exists() &&
                System.currentTimeMillis() - updatedAt < UPDATE_INTERVAL_MS
            ) return@withLock true
            downloadMutex.withLock {
                if (!force && isUnderMemoryPressure()) return@withLock false
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
        engineFilesOf(ownerOf(url)).forEach { staleEngineFiles += it }
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

    private fun loadCache(key: String, url: String, announce: Boolean = true) {
        val file = cacheFile(url)
        if (!file.exists()) return
        val compiled = compiledCacheFile(url)
        runCatching {
            val cached = if (compiled.exists()) runCatching { readCompiledCache(url, compiled) }.getOrNull() else null
            cached ?: parseAndStore(url, file, trusted = OfficialFilterCatalog.get(key) != null)
        }
            .onSuccess {
                storeRules(key, it, announce)
                loadedCacheKeys.add(key)
            }
            .onFailure {
                // Running out of memory says nothing about the cache; discarding it would only force a download.
                if (it is OutOfMemoryError) {
                    Log.e(TAG, "Not enough memory to load filter cache $key", it)
                } else {
                    file.delete()
                    compiled.delete()
                    Log.w(TAG, "Ignoring invalid filter cache $key", it)
                }
            }
    }

    /** The newer downloaded copy of a bundled list, when its compiled cache is intact; avoids parsing both copies. */
    private fun readDownloaded(url: String): NativeFilterRules? {
        val compiled = compiledCacheFile(url)
        if (!cacheFile(url).exists() || !compiled.exists()) return null
        return runCatching { readCompiledCache(url, compiled) }.getOrNull()
    }

    private fun readCompiledCache(url: String, compiled: File): NativeFilterRules? {
        val owner = ownerOf(url)
        val rules = NativeFilterRules.fromCompiled(compiled.readText()) { engineFile(owner, it) }
        val engine = rules.engine ?: return null
        return rules.takeIf { engine.file.isFile && engine.file.length() == engine.length }
    }

    private fun compiledCacheFile(url: String): File = File(cacheFile(url).path + ".meta")

    private fun ownerOf(url: String): String = cacheFile(url).name.removeSuffix(".txt")

    private fun engineFile(owner: String, sha256: String): File = File(sourcesDirectory, "$owner.${sha256.take(16)}.engine")

    private fun engineFilesOf(owner: String): List<File> =
        sourcesDirectory.listFiles { file -> file.name.startsWith("$owner.") && file.name.endsWith(".engine") }.orEmpty().toList()

    /** Parses [raw] into an engine file named after its contents, replacing whatever engine text the owner had. */
    private fun parseAndStore(url: String, raw: File, trusted: Boolean): NativeFilterRules =
        parseToEngine(ownerOf(url), raw, trusted).also { writeCompiledCache(url, it) }

    private fun parseToEngine(owner: String, raw: File, trusted: Boolean): NativeFilterRules {
        val temporary = File(sourcesDirectory, "$owner.engine.tmp")
        try {
            val parsed = parseHostFile(raw, temporary, trusted)
            val engine = checkNotNull(parsed.engine)
            val destination = engineFile(owner, engine.sha256)
            if (destination.isFile && destination.length() == engine.length) temporary.delete()
            else require(temporary.renameTo(destination)) { "Could not store the filter engine text" }
            engineFilesOf(owner).filter { it != destination }.forEach { staleEngineFiles += it }
            return parsed.withEngine(engine = engine.copy(file = destination))
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun loadBundled(source: OfficialFilter): NativeFilterRules {
        val owner = "bundled-${source.id}"
        val compiled = runCatching {
            val rules = context.assets.open("adblock/compiled/${source.id}.json").bufferedReader().use { reader ->
                NativeFilterRules.fromCompiled(reader.readText()) { engineFile(owner, it) }
            }
            val engine = checkNotNull(rules.engine)
            if (!engine.file.isFile || engine.file.length() != engine.length) {
                require(copyAsset("adblock/compiled/${source.id}.engine", engine.file) && engine.file.length() == engine.length)
            }
            rules
        }.getOrNull()
        if (compiled != null) return compiled
        val raw = File(context.cacheDir, "$owner.txt")
        try {
            context.assets.open("adblock/${source.fallbackAsset}.txt").use { input -> raw.outputStream().use { input.copyTo(it) } }
            return parseToEngine(owner, raw, trusted = true)
        } finally { raw.delete() }
    }

    private fun removeLegacyCaches() {
        sourcesDirectory.listFiles { file -> file.name.endsWith(".txt.json") }.orEmpty().forEach { it.delete() }
        File(context.cacheDir, "adblock-rust").listFiles { file -> !file.name.startsWith(ENGINE_CACHE_PREFIX) }.orEmpty().forEach { it.delete() }
    }

    /** Deletes the superseded engine text files once no loaded list refers to them. */
    private fun deleteStaleEngineFiles() {
        val referenced = rules.values.mapNotNullTo(hashSetOf()) { it.engine?.file }
        staleEngineFiles.removeAll { file -> (file !in referenced).also { if (it) file.delete() } }
    }

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
                    val temporary = File(cache.parentFile, "${cache.name}.tmp")
                    try {
                        connection.inputStream.use { input ->
                            temporary.outputStream().buffered().use { output ->
                                val buffer = ByteArray(16 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    require(total <= MAX_SOURCE_BYTES)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        val parsed = parseToEngine(ownerOf(sourceUrl), temporary, trusted = OfficialFilterCatalog.get(key) != null)
                        require(temporary.renameTo(cache))
                        writeCompiledCache(sourceUrl, parsed)
                        storeRules(key, parsed, announce = false)
                    } finally { temporary.delete() }
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
    private fun putRules(key: String, parsed: NativeFilterRules) = storeRules(key, parsed, announce = true)

    @Synchronized
    private fun storeRules(key: String, parsed: NativeFilterRules, announce: Boolean) {
        val stored = persistEngine(key, parsed).contentOnly()
        if (announce || settings.current.hasBlockingSites() && enabledSources(settings.current).any { it.first == key }) {
            rules = rules + (key to stored)
        }
        mutableRuleCounts.update { it + (key to parsed.supportedCount) }
        if (announce) mutableRevision.update { it + 1 } else announcePending.set(true)
    }

    /** Rules built from a string carry their engine text in memory; park it on disk like everything else. */
    private fun persistEngine(key: String, parsed: NativeFilterRules): NativeFilterRules {
        if (parsed.engine != null || parsed.engineSource.isEmpty()) return parsed
        val bytes = parsed.engineSource.toByteArray()
        val sha256 = RustFilterEngine.sha256(bytes)
        val file = File(File(context.cacheDir, "adblock-engine").apply { mkdirs() },
            "${RustFilterEngine.sha256(key.toByteArray()).take(16)}.${sha256.take(16)}.engine")
        file.writeBytes(bytes)
        return parsed.withEngine(engine = EngineList(file, sha256, bytes.size.toLong()))
    }

    private fun cacheFile(url: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(sourcesDirectory, "$hash.txt")
    }

    private companion object {
        const val TAG = "NativeFilterSources"
        const val CACHE_FORMAT_VERSION = 3
        const val MAX_SOURCE_BYTES = 20_000_000L
        const val UPDATE_INTERVAL_MS = 4L * 24 * 60 * 60 * 1000
        const val RETRY_INTERVAL_MS = 60L * 60 * 1000
        const val ENGINE_CACHE_PREFIX = "v2-"
        const val ENGINE_CACHE_ENTRIES = 2
    }
}
