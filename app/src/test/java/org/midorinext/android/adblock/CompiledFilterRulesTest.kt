package org.midorinext.android.adblock

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class CompiledFilterRulesTest {
    @Test
    fun bundledCompiledSnapshotsMatchTheirSourcesAndCurrentScriptletCatalogue() {
        val root = File("src/main/assets/adblock")
        for (source in OfficialFilterCatalog.all.filter { it.fallbackAsset != null }) {
            val compiled = File(root, "compiled/${source.id}.json").readText()
            val hash = MessageDigest.getInstance("SHA-256").digest(File(root, "${source.fallbackAsset}.txt").readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(source.id, hash, JSONObject(compiled).getString("sourceSha256"))
            assertTrue(NativeFilterRules.fromCompiled(compiled).supportedCount > 0)
        }
    }

    @Test
    fun compiledRulesPreserveNetworkExceptionsPrioritiesCosmeticsAndScriptlets() {
        val original = parseHostSource("""
            ||ads.example^
            @@||safe.ads.example^
            /banners/*${'$'}image,domain=news.example
            ||priority.example^${'$'}script,important
            @@||priority.example^${'$'}script
            @@||reader.example^${'$'}document
            ##.advertisement
            reader.example#@#.advertisement
            news.example##+js(set, advertising, false)
        """.trimIndent())
        val restored = NativeFilterRules.fromCompiled(original.compiled().toString())
        assertEquals(original.supportedCount, restored.supportedCount)
        assertTrue(restored.blocks("https://ads.example/banner.js", "news.example", "script"))
        assertFalse(restored.blocks("https://safe.ads.example/article.js", "news.example", "script"))
        assertTrue(restored.blocks("https://cdn.example/banners/top.png", "news.example", "image"))
        assertFalse(restored.blocks("https://cdn.example/banners/top.png", "other.example", "image"))
        assertEquals(RuleDecision.IMPORTANT_BLOCK, restored.decision("https://priority.example/ad.js", "news.example", "script"))
        assertTrue(restored.pageException("https://reader.example/article", "document"))
        assertFalse(restored.pageException("https://other.example/article", "document"))
        assertEquals(original.cosmetics.forHost("reader.example").toList(), restored.cosmetics.forHost("reader.example").toList())
        assertEquals(original.scriptlets, restored.scriptlets)
    }
}
