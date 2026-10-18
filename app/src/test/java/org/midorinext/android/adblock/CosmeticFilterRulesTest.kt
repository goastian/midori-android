package org.midorinext.android.adblock

import org.junit.Assert.*
import org.junit.Test

class CosmeticFilterRulesTest {
    @Test
    fun scopesAndExceptionsApplyAcrossSourcesWithoutHidingOtherSitesContent() {
        val rules = parseHostSource("""
            ##.sponsor-slot
            news.example,shop.example##.paid-placement
            ~reader.example##.ad-frame
            news.example#@#.sponsor-slot
            news.example##article:has(> .advertisement)
        """.trimIndent())
        val news = rules.cosmetics.forHost("www.news.example").toList()
        val policy = AdPlacementPolicy.message(true, CosmeticSelection(news))
        val selectors = policy.getJSONArray("selectors").let { values -> (0 until values.length()).map(values::getString) }
        assertFalse(".sponsor-slot" in selectors)
        assertTrue(".paid-placement" in selectors)
        assertTrue("article:has(> .advertisement)" in selectors)
        assertFalse(rules.cosmetics.forHost("other.example").any { it.selector == ".paid-placement" })
        assertFalse(rules.cosmetics.forHost("sub.reader.example").any { it.selector == ".ad-frame" })
        assertEquals(0, AdPlacementPolicy.message(false, CosmeticSelection(news)).getJSONArray("selectors").length())
    }

    @Test
    fun pageExceptionsCanDisableGenericHidingOrAllHiding() {
        val rules = parseHostSource("""
            ##.generic-ad
            news.example##.site-ad
        """.trimIndent()).cosmetics.forHost("news.example").toList()
        val genericDisabled = AdPlacementPolicy.message(true, CosmeticSelection(rules, genericEnabled = false))
        assertEquals(".site-ad", genericDisabled.getJSONArray("selectors").getString(0))
        assertEquals(1, genericDisabled.getJSONArray("selectors").length())
        assertFalse(AdPlacementPolicy.message(true, CosmeticSelection(rules, enabled = false)).getBoolean("enabled"))
    }

    @Test
    fun executableAndUnsupportedCosmeticRulesRemainExcluded() {
        for (line in listOf(
            "example.com##+js(set, adblock, false)", "example.com##^script:has-text(advertising)",
            "example.com##.ad:remove()", "example.com##.ad:style(display: none)",
            "example.*##.ad", "example.com##.ad { color: red }",
        )) assertNull(line, parseCosmeticRule(line))
    }
}
