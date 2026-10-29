package org.midorinext.android

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.adblock.*

class AdblockLifecycleDeviceTest {
    @Test
    fun fiftyCyclesReleaseUnneededEnginesAndPreserveSiteOverrides(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MidoriApplication
        val feature = app.nativeContentBlockingFeature.get()
        withTimeout(120_000) { feature.ready.await() }
        val directory = File(app.cacheDir, "adblock-lifecycle-test").apply { mkdirs() }
        val preferences = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val key = "lifecycle-test-$name"
                preferences.add(key)
                return app.getSharedPreferences(key, mode)
            }
        }
        val settings = AdBlockSettings(context)
        settings.setLevel(BlockingLevel.OFF)
        settings.current.officialEnabled.forEach { settings.setOfficialEnabled(it, false) }
        val urls = listOf("https://trackers.fixture.invalid/list", "https://ads.fixture.invalid/list")
        for ((index, url) in urls.withIndex()) {
            val cache = File(File(context.filesDir, "adblock-sources").apply { mkdirs() }, "${RustFilterEngine.sha256(url.toByteArray())}.txt")
            cache.writeText(if (index == 0) "||tracker.example^\n" else "||ads.example^\n##.fixture-ad\n")
            assertTrue(settings.addSource(url, tracker = index == 0))
            settings.sourceUpdated(url, System.currentTimeMillis())
        }
        context.getSharedPreferences("native_adblock_http", Context.MODE_PRIVATE).edit().putInt("format_version", 3).commit()
        val baselineHandles = RustAdblock.liveEngineCount()
        val sources = NativeFilterSources(context, settings)
        val running = AtomicBoolean(true)
        val peak = AtomicLong(Debug.getNativeHeapAllocatedSize())
        val sampler = thread(isDaemon = true) {
            while (running.get()) {
                peak.accumulateAndGet(Debug.getNativeHeapAllocatedSize(), ::maxOf)
                Thread.sleep(5)
            }
        }
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val phases = mutableListOf<String>()
        suspend fun check(level: BlockingLevel, site: BlockingLevel?, all: Boolean, trackers: Boolean) {
            settings.setSiteLevel("news.example", site)
            settings.setLevel(level)
            sources.prepareEngines(settings.current)
            assertEquals(all to trackers, sources.engineState())
            assertEquals(baselineHandles + (if (all) 1 else 0) + (if (trackers) 1 else 0), RustAdblock.liveEngineCount())
            val config = settings.current
            val effective = config.levelFor("https://news.example/")
            for ((url, blocked) in listOf("https://tracker.example/a.js" to (effective != BlockingLevel.OFF),
                "https://ads.example/a.js" to (effective == BlockingLevel.TRACKERS_AND_ADS))) {
                assertEquals("$level/$site: $url", blocked,
                    sources.networkDecision(config, effective, url, "https://news.example/", "script").optBoolean("cancel"))
            }
        }
        try {
            withTimeout(120_000) { sources.ready.await() }
            assertEquals(false to false, sources.engineState())
            assertTrue(sources.ruleCounts.value.isEmpty())
            check(BlockingLevel.TRACKERS_AND_ADS, null, true, false)
            val putRules = sources.javaClass.getDeclaredMethod("putRules", String::class.java, NativeFilterRules::class.java).apply { isAccessible = true }
            putRules.invoke(sources, urls[0], parseHostSource("||tracker.example^\n||updated-tracker.example^\n"))
            @Suppress("UNCHECKED_CAST")
            val loaded = sources.javaClass.getDeclaredField("rules").apply { isAccessible = true }.get(sources) as Map<String, NativeFilterRules>
            val engineFile = loaded.getValue(urls[0]).engine!!.file
            val saved = engineFile.readBytes()
            assertTrue(engineFile.delete())
            sources.prepareEngines(settings.current)
            assertEquals(baselineHandles + 1, RustAdblock.liveEngineCount())
            assertTrue(sources.networkDecision(settings.current, settings.current.level, "https://tracker.example/a.js", "https://news.example/", "script").optBoolean("cancel"))
            assertFalse(sources.networkDecision(settings.current, settings.current.level, "https://updated-tracker.example/a.js", "https://news.example/", "script").optBoolean("cancel"))
            engineFile.writeBytes(saved)
            sources.prepareEngines(settings.current)
            assertTrue(sources.networkDecision(settings.current, settings.current.level, "https://updated-tracker.example/a.js", "https://news.example/", "script").optBoolean("cancel"))
            File(context.cacheDir, "adblock-rust").listFiles().orEmpty().filter { it.extension == "dat" }.forEach { it.writeText("invalid cache") }
            check(BlockingLevel.OFF, null, false, false)
            check(BlockingLevel.TRACKERS_AND_ADS, null, true, false)
            repeat(50) { cycle ->
                check(BlockingLevel.TRACKERS_AND_ADS, BlockingLevel.TRACKERS, true, true)
                check(BlockingLevel.TRACKERS_AND_ADS, null, true, false)
                check(BlockingLevel.TRACKERS, null, false, true)
                check(BlockingLevel.OFF, BlockingLevel.TRACKERS_AND_ADS, true, false)
                check(BlockingLevel.OFF, BlockingLevel.TRACKERS, false, true)
                check(BlockingLevel.OFF, null, false, false)
                phases += "cycle=$cycle native=${Debug.getNativeHeapAllocatedSize()} handles=${RustAdblock.liveEngineCount()}"
            }
            val metrics = "cycles=50 nativeBefore=$nativeBefore nativePeak=${peak.get()} nativeAfter=${Debug.getNativeHeapAllocatedSize()} " + phases.joinToString(";")
            Log.i("AdblockLifecycle", metrics)
            InstrumentationRegistry.getInstrumentation().sendStatus(2, android.os.Bundle().apply { putString("adblock_metric", metrics) })
        } finally {
            running.set(false)
            sampler.join()
            sources.close()
            assertEquals(baselineHandles, RustAdblock.liveEngineCount())
            directory.deleteRecursively()
            preferences.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }
}
