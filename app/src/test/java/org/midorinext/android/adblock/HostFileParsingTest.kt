package org.midorinext.android.adblock

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class HostFileParsingTest {
    private val subscription = """
        ! Title: fixture
        [Adblock Plus 2.0]
        !#if env_firefox
        ||firefox-only.example^${'$'}script
        !#else
        ||other-browsers.example^${'$'}script
        !#endif
        !#if env_chromium
        ||chromium-only.example^${'$'}script
        !#endif
        ||ads.example^
        @@||safe.ads.example^
        /banners/*${'$'}image,domain=news.example
        ||priority.example^${'$'}script,important
        @@||priority.example^${'$'}script
        @@||reader.example^${'$'}document
        @@||generic.example^${'$'}generichide
        ||disabled.example^${'$'}script
        ||disabled.example^${'$'}script,badfilter
        0.0.0.0 hosts-blocked.example localhost tracker.example # comment
        ##.advertisement
        reader.example#@#.advertisement
        news.example##+js(set, advertising, false)
        news.example#%#//scriptlet('abort-on-property-read', 'ads')
    """.trimIndent()

    private fun <T> withFiles(block: (raw: File, engine: File) -> T): T {
        val directory = java.nio.file.Files.createTempDirectory("midori-parse").toFile()
        try {
            val raw = File(directory, "list.txt").also { it.writeText(subscription) }
            return block(raw, File(directory, "list.engine"))
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun streamingParsingProducesTheSameEngineTextAndRulesAsParsingInMemory() = withFiles { raw, engineFile ->
        val memory = parseHostSource(subscription, trusted = true)
        val streamed = parseHostFile(raw, engineFile, trusted = true)
        assertEquals(memory.engineSource, engineFile.readText())
        val engine = checkNotNull(streamed.engine)
        assertEquals(RustFilterEngine.sha256(memory.engineSource.toByteArray()), engine.sha256)
        assertEquals(memory.engineSource.toByteArray().size.toLong(), engine.length)
        assertEquals(memory.supportedCount, streamed.supportedCount)
        assertEquals(memory.scriptlets, streamed.scriptlets)
        for (url in listOf("https://reader.example/a", "https://other.example/a", "https://generic.example/a")) {
            for (type in listOf("document", "elemhide", "generichide")) {
                assertEquals("$url $type", memory.pageException(url, type), streamed.pageException(url, type))
            }
        }
        assertEquals(memory.documentExceptions(false).map { it.toString() }, streamed.documentExceptions(false).map { it.toString() })
        assertTrue(streamed.pageException("https://reader.example/a", "document"))
        assertTrue(streamed.scriptlets.isNotEmpty())
    }

    @Test
    fun theEngineTextKeepsEnvironmentBranchesHostsAndBadfiltersButNotScriptletsOrComments() = withFiles { raw, engineFile ->
        parseHostFile(raw, engineFile, trusted = true)
        val lines = engineFile.readLines()
        assertTrue("||firefox-only.example^${'$'}script" in lines)
        assertFalse("||other-browsers.example^${'$'}script" in lines)
        assertFalse("||chromium-only.example^${'$'}script" in lines)
        assertTrue("||hosts-blocked.example^" in lines)
        assertTrue("||tracker.example^" in lines)
        assertTrue("||disabled.example^${'$'}script,badfilter" in lines)
        assertTrue(lines.none { it.startsWith("!") || it.contains("+js(") || it.contains("#%#") })
    }

    @Test
    fun theLeanResultDoesNotRetainNetworkOrCosmeticIndexes() = withFiles { raw, engineFile ->
        val streamed = parseHostFile(raw, engineFile, trusted = true)
        assertFalse(streamed.blocks("https://ads.example/banner.js", "news.example", "script"))
        assertEquals(0, streamed.cosmetics.rules.size)
        assertEquals("", streamed.engineSource)
    }

    @Test
    fun aListWithoutAnySupportedRuleIsRejected() {
        val directory = java.nio.file.Files.createTempDirectory("midori-parse").toFile()
        try {
            val raw = File(directory, "list.txt").also { it.writeText("! only a comment\n") }
            assertThrows(IllegalArgumentException::class.java) { parseHostFile(raw, File(directory, "list.engine")) }
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun theBundledSubscriptionsParseToTheSameEngineTextAsTheirRecordedHash() {
        val root = File("src/main/assets/adblock")
        val directory = java.nio.file.Files.createTempDirectory("midori-parse").toFile()
        try {
            for (source in OfficialFilterCatalog.all.filter { it.fallbackAsset != null }) {
                val streamed = parseHostFile(File(root, "${source.fallbackAsset}.txt"), File(directory, "${source.id}.engine"), trusted = true)
                val recorded = NativeFilterRules.fromCompiled(File(root, "compiled/${source.id}.json").readText()) { File(root, "compiled/${source.id}.engine") }
                assertEquals(source.id, recorded.engine?.sha256, streamed.engine?.sha256)
                assertEquals(source.id, recorded.supportedCount, streamed.supportedCount)
                assertEquals(source.id, recorded.scriptlets, streamed.scriptlets)
            }
        } finally { directory.deleteRecursively() }
    }
}
