package org.midorinext.android.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesktopHostRulesTest {
    private val rules = DesktopHostRules(
        blocked = "ads.example\ntracker.example\n",
        allowed = "login.ads.example\n",
    )

    @Test
    fun matchesHostsAndSubdomainsWithoutMatchingLookalikes() {
        assertTrue(rules.blocks("https://ads.example/banner"))
        assertTrue(rules.blocks("https://cdn.ads.example/frame"))
        assertTrue(rules.blocks("https://tracker.example/pixel"))
        assertFalse(rules.blocks("https://notads.example/frame"))
        assertFalse(rules.blocks("https://ads.example.evil.test/frame"))
    }

    @Test
    fun exceptionsWinAndNonWebSchemesAreIgnored() {
        assertFalse(rules.blocks("https://login.ads.example/account"))
        assertFalse(rules.blocks("https://child.login.ads.example/account"))
        assertFalse(rules.blocks("file:///ads.example/frame"))
        assertFalse(rules.blocks("about:blank"))
    }

    @Test
    fun desktopDerivedHostsAreBundled() {
        val root = File("src/main/assets/adblock")
        val ads = DesktopHostRules(
            File(root, "ads.txt").readText(), File(root, "ads_exceptions.txt").readText(),
        )
        val trackers = DesktopHostRules(
            File(root, "trackers.txt").readText(), File(root, "trackers_exceptions.txt").readText(),
        )
        assertTrue(File(root, "ads.txt").readLines().size > 50_000)
        assertTrue(File(root, "trackers.txt").readLines().size > 40_000)
        assertTrue(ads.blocks("https://adnxs.com/ad-frame"))
        assertTrue(ads.blocks("https://sub.adnxs.com/ad-frame"))
        assertFalse(trackers.blocks("https://cbsi.map.fastly.net/login"))
    }
}
