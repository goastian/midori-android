package org.midorinext.android.adblock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.mozac.GeckoPreferences
import org.mozilla.geckoview.ContentBlocking
import java.io.File

class CookieBannerPolicyTest {
    @Test
    fun cookieHandlingFollowsTheEffectiveSiteLevelAndItsOwnSwitch() {
        val config = AdBlockConfiguration(siteLevels = mapOf("example.org" to BlockingLevel.OFF))
        assertFalse(config.rejectsCookieBannersFor("https://example.org/article"))
        assertTrue(config.rejectsCookieBannersFor("https://other.org/article"))
        assertTrue(config.copy(level = BlockingLevel.TRACKERS).rejectsCookieBannersFor("https://other.org"))
        assertFalse(config.copy(rejectCookieBanners = false).rejectsCookieBannersFor("https://other.org"))
        assertFalse(config.copy(level = BlockingLevel.OFF).rejectsCookieBannersFor("https://other.org"))
        assertTrue(config.copy(level = BlockingLevel.OFF, siteLevels = mapOf("example.org" to BlockingLevel.TRACKERS))
            .rejectsCookieBannersFor("https://example.org/article"))
    }

    @Test
    fun internalPagesNeverReceiveAnEnabledPolicy() {
        listOf("about:blank", "moz-extension://example.org/index.html", "file:///example.org", "invalid", "").forEach { url ->
            assertFalse(CookieBannerPolicy.message(AdBlockConfiguration(), url).getBoolean("enabled"))
        }
    }

    @Test
    fun textMatchingOnlyRecognizesExplicitRejectionWithinABanner() {
        val policy = CookieBannerPolicy.message(AdBlockConfiguration(), "https://example.org")
        val pattern = Regex(policy.getString("rejectText"), RegexOption.IGNORE_CASE)
        listOf("Reject all", "Reject all cookies", "Only necessary cookies", "Rechazar todas", "Continuar sin aceptar",
            "Tout refuser", "Alle ablehnen", "Rifiuta tutti").forEach { assertTrue(it, pattern.matches(it)) }
        listOf("Accept all", "Aceptar todas", "Continue", "OK", "Manage preferences", "No", "Do not reject all")
            .forEach { assertFalse(it, pattern.matches(it)) }
    }

    @Test
    fun privateSessionsKeepFirstPartyCookiesAndBlockThirdPartyCookies() {
        assertEquals(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY, GeckoPreferences.privateCookieBehavior(true))
        assertEquals(ContentBlocking.CookieBehavior.ACCEPT_ALL, GeckoPreferences.privateCookieBehavior(false))
    }

    @Test
    fun oldCookieExtensionIsRemovedOnUpgradeAndBridgeCannotRestoreCookies() {
        assertTrue("qwant-cookie-android@qwant.com" in LegacyBlockerMigration.LEGACY_IDS)
        assertFalse(File("src/main/assets/midori_cookies/manifest.json").exists())
        val manifest = JSONObject(File("src/main/assets/adblock/content/manifest.json").readText())
        assertEquals(NativeContentBlockingFeature.EXTENSION_ID,
            manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id"))
        val permissions = manifest.getJSONArray("permissions")
        assertFalse((0 until permissions.length()).map(permissions::getString).any { it == "cookies" || it == "storage" })
        assertFalse(manifest.getJSONArray("content_scripts").getJSONObject(0).getBoolean("all_frames"))
    }
}
