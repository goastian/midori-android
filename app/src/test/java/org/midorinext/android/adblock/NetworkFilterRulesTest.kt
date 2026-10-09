package org.midorinext.android.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NetworkFilterRulesTest {
    @Test
    fun bundledRulesBlockGoogleAndOtherNetworksAcrossResourceTypes() {
        val root = File("src/main/assets/adblock")
        val rules = NativeFilterRules.bundled(File(root, "ads.txt").readText(), File(root, "ads_exceptions.txt").readText())
        for (type in listOf("script", "image", "xmlhttprequest", "sub_frame", "stylesheet", "media", "ping")) {
            for (url in listOf(
                "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
                "https://securepubads.g.doubleclick.net/tag/js/gpt.js",
                "https://tpc.googlesyndication.com/simgad/creative.png",
                "https://ib.adnxs.com/auction",
                "https://bidder.criteo.com/bid",
            )) assertTrue("$type: $url", rules.blocks(url, "publisher.example", type))
            assertFalse(rules.blocks("https://fonts.googleapis.com/css2?family=Roboto", "publisher.example", type))
            assertFalse(rules.blocks("https://accounts.google.com/signin", "publisher.example", type))
        }
    }

    @Test
    fun resourceTypesAndNegationsArePreserved() {
        val rules = parseHostSource("""
            ||ads.example^${'$'}script,image
            ||video.example^${'$'}~media
            ||xhr.example^${'$'}xhr
        """.trimIndent())
        assertTrue(rules.blocks("https://ads.example/ad.js", type = "script"))
        assertTrue(rules.blocks("https://ads.example/ad.png", type = "image"))
        assertFalse(rules.blocks("https://ads.example/iframe", type = "sub_frame"))
        assertTrue(rules.blocks("https://video.example/beacon", type = "ping"))
        assertFalse(rules.blocks("https://video.example/movie.mp4", type = "media"))
        assertTrue(rules.blocks("https://xhr.example/bid", type = "xmlhttprequest"))
        assertFalse(rules.blocks("https://xhr.example/app.js", type = "script"))
    }

    @Test
    fun firstAndThirdPartyRulesDoNotBecomeUnconditionalHostBlocks() {
        val rules = parseHostSource("""
            ||ads.example^${'$'}third-party,script
            ||news.example/ads/*${'$'}first-party,image
        """.trimIndent())
        assertTrue(rules.blocks("https://ads.example/ad.js", "news.example", "script", true))
        assertFalse(rules.blocks("https://ads.example/ad.js", "ads.example", "script", false))
        assertTrue(rules.blocks("https://news.example/ads/banner.png", "news.example", "image", false))
        assertFalse(rules.blocks("https://news.example/ads/banner.png", "other.example", "image", true))
        assertFalse(rules.blocks("https://news.example/photos/article.png", "news.example", "image", false))
    }

    @Test
    fun exceptionsKeepTheirTypesAndSiteConditions() {
        val rules = parseHostSource("""
            ||cdn.example^${'$'}third-party
            @@||cdn.example/player.js${'$'}script,domain=video.example|~private.video.example
        """.trimIndent())
        assertFalse(rules.blocks("https://cdn.example/player.js", "video.example", "script"))
        assertTrue(rules.blocks("https://cdn.example/player.js", "private.video.example", "script"))
        assertTrue(rules.blocks("https://cdn.example/player.js", "video.example", "image"))
        assertTrue(rules.blocks("https://cdn.example/ad.js", "video.example", "script"))
    }

    @Test
    fun importantRulesHavePriorityOverOrdinaryExceptions() {
        val rules = parseHostSource("""
            ||ads.example^${'$'}script,important
            @@||ads.example^${'$'}script
            @@||ads.example/safe.js${'$'}script,important
        """.trimIndent())
        assertEquals(RuleDecision.IMPORTANT_BLOCK, rules.decision("https://ads.example/ad.js", "news.example", "script"))
        assertEquals(RuleDecision.IMPORTANT_ALLOW, rules.decision("https://ads.example/safe.js", "news.example", "script"))
        assertFalse(rules.blocks("https://ads.example/image.png", "news.example", "image"))
    }

    @Test
    fun unsupportedModifiersAndConflictingPartyConditionsRemainIgnored() {
        val rules = parseHostSource("""
            ||safe.example^${'$'}script,redirect=noopjs
            ||safe.example^${'$'}script,third-party,first-party
            ||blocked.example^${'$'}script
        """.trimIndent())
        assertFalse(rules.blocks("https://safe.example/app.js", type = "script"))
        assertTrue(rules.blocks("https://blocked.example/ad.js", type = "script"))
    }

    @Test
    fun siteOverridesKeepTheirListsActiveWhenTheGlobalLevelIsOff() {
        val config = AdBlockConfiguration(level = BlockingLevel.OFF, siteLevels = mapOf("news.example" to BlockingLevel.TRACKERS_AND_ADS))
        assertTrue(config.hasBlockingSites())
        assertTrue(config.hasAdBlockingSites())
        assertFalse(config.copy(siteLevels = emptyMap()).hasBlockingSites())
        assertFalse(config.copy(siteLevels = mapOf("news.example" to BlockingLevel.TRACKERS)).hasAdBlockingSites())
    }
}
