package org.midorinext.android.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockConfigurationTest {
    @Test
    fun siteOverrideWinsOverDefaultLevel() {
        val config = AdBlockConfiguration(
            level = BlockingLevel.TRACKERS_AND_ADS,
            siteLevels = mapOf("example.org" to BlockingLevel.OFF),
        )
        assertEquals(BlockingLevel.OFF, config.levelFor("https://example.org/page"))
        assertEquals(BlockingLevel.TRACKERS_AND_ADS, config.levelFor("https://other.org/page"))
    }

    @Test
    fun customSourceAcceptsOnlySupportedHostRules() {
        val rules = parseHostSource("""
            ! Comment
            ||ads.example^
            ||TRACKER.EXAMPLE^
            @@||safe.ads.example^
            ||ads.example/path
            example.org##.banner
        """.trimIndent())
        assertTrue(rules.blocks("https://ads.example/frame"))
        assertTrue(rules.blocks("https://tracker.example/frame"))
        assertFalse(rules.blocks("https://safe.ads.example/frame"))
        assertFalse(rules.blocks("https://example.org/frame"))
        assertThrows(IllegalArgumentException::class.java) { parseHostSource("example.org##.banner") }
    }

    @Test
    fun hostPathAndSiteConditionsApplyOnlyToMatchingSubframes() {
        val rules = parseHostSource("""
            ||ads.example/banner*${'$'}third-party,subdocument
            ||analytics.example^${'$'}domain=news.example|~private.news.example
            @@||ads.example/banner/safe^${'$'}domain=news.example
            ||images.example^${'$'}image
            example.org##.ad
        """.trimIndent())
        assertTrue(rules.blocks("https://ads.example/banner/one", "news.example"))
        assertFalse(rules.blocks("https://ads.example/other", "news.example"))
        assertFalse(rules.blocks("https://ads.example/banner/safe/frame", "news.example"))
        assertTrue(rules.blocks("https://analytics.example/pixel", "news.example"))
        assertFalse(rules.blocks("https://analytics.example/pixel", "private.news.example"))
        assertFalse(rules.blocks("https://images.example/pixel", "news.example"))
    }

    @Test
    fun unsupportedRuleTypesDoNotBroadenBlocking() {
        val rules = parseHostSource("""
            ||ads.example^${'$'}image
            ||ads.example^${'$'}redirect=noopjs
            ||frame.example^${'$'}subdocument
            @@||frame.example/allowed^${'$'}subdocument
        """.trimIndent())
        assertFalse(rules.blocks("https://ads.example/frame"))
        assertTrue(rules.blocks("https://frame.example/ad"))
        assertFalse(rules.blocks("https://frame.example/allowed/item"))
    }

    @Test
    fun officialSubscriptionsHaveUniqueSecureUrlsAndRegionalDefaults() {
        assertEquals(OfficialFilterCatalog.all.size, OfficialFilterCatalog.all.map { it.id }.distinct().size)
        assertTrue(OfficialFilterCatalog.all.all { it.url.startsWith("https://") })
        val spanish = OfficialFilterCatalog.defaults("es")
        assertTrue("easylist" in spanish)
        assertTrue("easyprivacy" in spanish)
        assertTrue("regional-spanish" in spanish)
        assertTrue("regional-spanish-portuguese" in spanish)
        assertFalse("regional-german" in spanish)
    }
}
