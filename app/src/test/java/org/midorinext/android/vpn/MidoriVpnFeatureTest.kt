package org.midorinext.android.vpn

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidoriVpnFeatureTest {
    @Test
    fun verifiedMidoriVpnBundleIsPackagedWithItsPopupAndProxyPermissions() {
        val manifest = JSONObject(asset("manifest.json").readText())
        val metadata = JSONObject(asset("upstream.json").readText())
        val gecko = manifest
            .getJSONObject("browser_specific_settings")
            .getJSONObject("gecko")
        val permissions = manifest.getJSONArray("permissions").let { values ->
            List(values.length()) { index -> values.getString(index) }.toSet()
        }
        val hostPermissions = manifest.getJSONArray("host_permissions").let { values ->
            List(values.length()) { index -> values.getString(index) }.toSet()
        }

        assertEquals(3, manifest.getInt("manifest_version"))
        assertEquals("MidoriVPN", manifest.getString("name"))
        assertEquals(MidoriVpnFeature.EXTENSION_ID, gecko.getString("id"))
        assertEquals("popup.html", manifest.getJSONObject("action").getString("default_popup"))
        assertEquals(manifest.getString("version"), metadata.getString("version"))
        assertEquals(1, metadata.getInt("compatibilityRevision"))
        assertEquals(
            "${metadata.getString("sourceVersion")}.${metadata.getInt("compatibilityRevision")}",
            metadata.getString("version"),
        )
        assertEquals("https://github.com/goastian/midorivpn-extension", metadata.getString("sourceRepository"))
        assertEquals(
            "midorivpn-extension-${metadata.getString("sourceVersion")}.zip",
            metadata.getJSONObject("release").getJSONObject("firefoxAsset").getString("name"),
        )
        assertTrue(metadata.getString("bundleSha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(
            metadata
                .getJSONObject("release")
                .getJSONObject("firefoxAsset")
                .getString("sha256")
                .matches(Regex("[0-9a-f]{64}")),
        )
        assertTrue(setOf("proxy", "webRequest", "webRequestBlocking").all(permissions::contains))
        assertEquals(setOf("<all_urls>"), hostPermissions)
        assertTrue(asset("background.js").readText().contains("proxy.onRequest.addListener"))
        assertTrue(asset("popup.html").readText().contains("<title>MidoriVPN</title>"))
        assertTrue(asset("icons/icon64.png").isFile)
        assertTrue(asset("LICENSE.upstream").readText().contains("GNU AFFERO GENERAL PUBLIC LICENSE"))
        assertFalse(manifest.toString().contains("ten - VPN"))
    }

    @Test
    fun changedBundledVersionTriggersAReinstall() {
        assertTrue(MidoriVpnFeature.needsReinstall("1.0.19", "1.0.19.1"))
        assertTrue(MidoriVpnFeature.needsReinstall(null, "1.0.19.1"))
        assertFalse(MidoriVpnFeature.needsReinstall("1.0.19.1", "1.0.19.1"))
        assertFalse(MidoriVpnFeature.needsReinstall("1.0.19", null))
    }

    private fun asset(path: String) = File("src/main/assets/extensions/midori_vpn", path)
}
