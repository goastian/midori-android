package org.midorinext.android.adblock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidoriPrivacyAssetsTest {
    @Test
    fun midoriPrivacyAssetsArePackaged() {
        val manifest = JSONObject(asset("extensions/midori_privacy/manifest.json").readText())
        val gecko = manifest
            .getJSONObject("browser_specific_settings")
            .getJSONObject("gecko")

        assertEquals("midori-privacy@astian.org", gecko.getString("id"))
        val script = asset("extensions/midori_privacy/adblock_cosmetic.js").readText()
        val manifestText = manifest.toString()
        assertTrue(script.contains("__midoriPrivacyRunAdBlocker"))
        assertTrue(manifestText.contains("youtube.com"))
        assertTrue(manifestText.contains("astiango.com"))
        assertFalse(manifestText.contains("nativeMessaging"))
        assertFalse(script.contains("sendNativeMessage"))
        assertFalse(script.contains("connectNative"))
        assertFalse(script.contains("querySelectorAll"))
        assertFalse(script.contains("MutationObserver"))
        assertTrue(asset("blocklist_balanced.txt").readText().contains("doubleclick.net"))
        assertTrue(asset("blocklist_aggressive.txt").readText().contains("googlesyndication"))
    }

    private fun asset(path: String) = File("src/main/assets", path)
}
