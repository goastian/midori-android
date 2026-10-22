package org.midorinext.android

import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.adblock.AdBlockSettings
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.adblock.NativeFilterSources
import org.midorinext.android.adblock.RustFilterEngine
import org.midorinext.android.adblock.parseHostFile
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class AdblockMemoryDeviceTest {
    private val runtime = Runtime.getRuntime()
    private var keepAlive: Any? = null
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication

    /** A redirect to a neutral resource counts as blocked: the request never reaches the tracker. */
    private fun org.json.JSONObject.isBlocked() = optBoolean("cancel") || optBoolean("blocked")

    private fun javaUsedMb() = (runtime.totalMemory() - runtime.freeMemory()) / MB
    private fun nativeUsedMb() = Debug.getNativeHeapAllocatedSize() / MB

    private fun settle() {
        repeat(3) { runtime.gc(); System.runFinalization(); Thread.sleep(300) }
    }

    @Test
    fun loadingTheFilterEngineKeepsTheJavaHeapSmall(): Unit = runBlocking {
        val feature = app.nativeContentBlockingFeature.get()
        val existing = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { existing.ready.await() }
        existing.prepareEngines(app.adBlockSettings.get().current)
        settle()

        val baselineJava = javaUsedMb()
        val baselineNative = nativeUsedMb()
        val peakJava = AtomicLong(baselineJava)
        val running = AtomicBoolean(true)
        val sampler = thread(isDaemon = true) {
            while (running.get()) {
                peakJava.accumulateAndGet(javaUsedMb(), ::maxOf)
                Thread.sleep(2)
            }
        }
        val started = System.nanoTime()
        val settings = AdBlockSettings(app)
        val sources = NativeFilterSources(app, settings)
        keepAlive = sources
        withTimeout(180_000) { sources.ready.await() }
        sources.prepareEngines(settings.current)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        running.set(false)
        sampler.join()
        val peakDelta = peakJava.get() - baselineJava
        settle()
        val retainedJava = javaUsedMb() - baselineJava
        val retainedNative = nativeUsedMb() - baselineNative
        Log.i(TAG, "RESULT maxHeap=${runtime.maxMemory() / MB}MB baselineJava=${baselineJava}MB peakJavaDelta=${peakDelta}MB " +
            "retainedJavaDelta=${retainedJava}MB retainedNativeDelta=${retainedNative}MB elapsed=${elapsedMs}ms")
        val config = settings.current
        assertTrue(sources.networkDecision(config, config.level, "https://googleads.g.doubleclick.net/pagead/id", "https://news.example/", "script").optBoolean("cancel"))
        assertFalse(sources.networkDecision(config, config.level, "https://news.example/story.html", "https://news.example/", "document").optBoolean("cancel"))
        assertTrue("Java heap peak delta ${peakDelta}MB", peakDelta <= MAX_PEAK_DELTA_MB)
        assertTrue("Java heap retained delta ${retainedJava}MB", retainedJava <= MAX_RETAINED_DELTA_MB)
    }

    /** Leaves only [limitMb] of the Java heap usable, like a device whose heap limit is that small. */
    private fun <T> withHeapLimit(limitMb: Long, block: () -> T): T {
        val ballast = ArrayList<ByteArray>()
        var reserved = 0L
        while (reserved < runtime.maxMemory() / MB - limitMb) {
            ballast += ByteArray(4 * 1024 * 1024)
            reserved += 4
        }
        try {
            return block()
        } finally {
            ballast.clear()
            settle()
        }
    }

    @Test
    fun loadingTheFilterEngineSucceedsOnADeviceWithASmallJavaHeap(): Unit = runBlocking {
        val feature = app.nativeContentBlockingFeature.get()
        val existing = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { existing.ready.await() }
        existing.prepareEngines(app.adBlockSettings.get().current)
        settle()
        withHeapLimit(SMALL_HEAP_MB) {
            val settings = AdBlockSettings(app)
            val sources = NativeFilterSources(app, settings)
            keepAlive = sources
            runBlocking {
                withTimeout(180_000) { sources.ready.await() }
                sources.prepareEngines(settings.current)
            }
            Log.i(TAG, "SMALL_HEAP loaded with ${SMALL_HEAP_MB}MB usable, javaUsed=${javaUsedMb()}MB")
        }
    }

    @Test
    fun theScriptletPolicySentToTheExtensionStaysSmall(): Unit = runBlocking {
        val feature = app.nativeContentBlockingFeature.get()
        val sources = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { sources.ready.await() }
        val config = app.adBlockSettings.get().current
        settle()
        val before = javaUsedMb()
        val started = System.nanoTime()
        val policy = sources.scriptletPolicy(config)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        settle()
        val heapDelta = javaUsedMb() - before
        val parsed = org.json.JSONObject(policy)
        Log.i(TAG, "SCRIPTLET_POLICY rules=${parsed.getJSONArray("rules").length()} exceptions=${parsed.getJSONArray("documentExceptions").length()} " +
            "chars=${policy.length} buildMs=$elapsed retainedMb=$heapDelta")
        assertTrue("Scriptlet policy retained ${heapDelta}MB", heapDelta <= MAX_POLICY_DELTA_MB)
    }

    @Test
    fun theTrackersOnlyEngineIsBuiltOnDemandAndLeavesAdsAlone(): Unit = runBlocking {
        val feature = app.nativeContentBlockingFeature.get()
        val existing = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { existing.ready.await() }
        val settings = AdBlockSettings(app)
        val sources = NativeFilterSources(app, settings)
        keepAlive = sources
        withTimeout(180_000) { sources.ready.await() }
        val full = settings.current.copy(level = BlockingLevel.TRACKERS_AND_ADS, siteLevels = emptyMap())
        val trackersOnly = full.copy(level = BlockingLevel.TRACKERS)
        val analytics = "https://www.google-analytics.com/analytics.js"
        val advertising = "https://googleads.g.doubleclick.net/pagead/id"
        fun blocked(config: org.midorinext.android.adblock.AdBlockConfiguration, level: BlockingLevel, url: String) =
            sources.networkDecision(config, level, url, "https://news.example/", "script").isBlocked()

        sources.prepareEngines(full)
        assertTrue("ads are blocked at the full level", blocked(full, BlockingLevel.TRACKERS_AND_ADS, advertising))
        assertFalse("no tracker engine exists until a tracker-only level is configured", blocked(full, BlockingLevel.TRACKERS, analytics))
        sources.prepareEngines(trackersOnly)
        assertTrue("trackers are blocked", blocked(trackersOnly, BlockingLevel.TRACKERS, analytics))
        assertFalse("ads are left alone", blocked(trackersOnly, BlockingLevel.TRACKERS, advertising))
        assertTrue("the full engine still blocks ads", blocked(trackersOnly, BlockingLevel.TRACKERS_AND_ADS, advertising))
    }

    @Test
    fun refreshingEveryListSucceedsOnADeviceWithASmallJavaHeap(): Unit = runBlocking {
        val feature = app.nativeContentBlockingFeature.get()
        val existing = feature.javaClass.getDeclaredField("sources").apply { isAccessible = true }.get(feature) as NativeFilterSources
        withTimeout(120_000) { existing.ready.await() }
        settle()
        app.getSharedPreferences("native_adblock_http", 0).edit().clear().commit()
        withHeapLimit(SMALL_HEAP_MB) {
            val settings = AdBlockSettings(app)
            val sources = NativeFilterSources(app, settings)
            keepAlive = sources
            runBlocking {
                withTimeout(180_000) { sources.ready.await() }
                val started = System.nanoTime()
                val updated = withTimeout(600_000) { sources.updateEnabled(force = true) }
                sources.prepareEngines(settings.current)
                Log.i(TAG, "SMALL_HEAP refreshed all lists=$updated in ${(System.nanoTime() - started) / 1_000_000}ms")
                assertTrue("every list refreshed", updated)
                val config = settings.current
                assertTrue(sources.networkDecision(config, config.level, "https://googleads.g.doubleclick.net/pagead/id", "https://news.example/", "script").optBoolean("cancel"))
            }
        }
    }

    @Test
    fun parsingTheLargestDownloadedListSucceedsOnADeviceWithASmallJavaHeap() {
        val directory = File(app.filesDir, "adblock-sources")
        val largest = directory.listFiles { file -> file.name.endsWith(".txt") }.orEmpty().maxByOrNull { it.length() }
            ?: return Log.i(TAG, "No downloaded list to parse").let { }
        val engine = File(app.cacheDir, "memory-probe.engine")
        try {
            withHeapLimit(SMALL_HEAP_MB) {
                val started = System.nanoTime()
                val parsed = parseHostFile(largest, engine, trusted = false)
                Log.i(TAG, "SMALL_HEAP parsed ${largest.length() / MB}MB list: ${parsed.supportedCount} rules, " +
                    "${parsed.scriptlets.size} scriptlets in ${(System.nanoTime() - started) / 1_000_000}ms")
                RustFilterEngine.compileFiles(listOf(engine)).close()
            }
        } finally { engine.delete() }
    }

    private companion object {
        const val TAG = "AdblockMemory"
        const val MB = 1024L * 1024L
        const val SMALL_HEAP_MB = 100L
        const val MAX_PEAK_DELTA_MB = 150L
        const val MAX_RETAINED_DELTA_MB = 35L
        const val MAX_POLICY_DELTA_MB = 12L
    }
}
