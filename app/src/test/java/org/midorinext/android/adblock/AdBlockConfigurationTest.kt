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
}
