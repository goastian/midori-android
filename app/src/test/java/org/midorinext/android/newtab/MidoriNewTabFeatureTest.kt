package org.midorinext.android.newtab

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidoriNewTabFeatureTest {
    @Test
    fun buildsPageUrlFromGeckoExtensionOrigin() {
        assertEquals(
            "moz-extension://midori-runtime-id/index.html",
            MidoriNewTabFeature.pageUrl("moz-extension://midori-runtime-id/"),
        )
        assertEquals(
            "moz-extension://midori-runtime-id/index.html",
            MidoriNewTabFeature.pageUrl("moz-extension://midori-runtime-id"),
        )
    }

    @Test
    fun officialFirefoxBundleAndMetadataAreVendoredTogether() {
        val manifest = JSONObject(asset("manifest.json").readText())
        val metadata = JSONObject(asset("upstream.json").readText())
        val gecko = manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko")

        assertEquals(2, manifest.getInt("manifest_version"))
        assertEquals(MidoriNewTabFeature.EXTENSION_ID, gecko.getString("id"))
        assertTrue(!manifest.has("chrome_url_overrides"))
        assertTrue(!manifest.has("commands"))
        assertEquals(manifest.getString("version"), metadata.getString("version"))
        assertEquals(6, metadata.getInt("compatibilityRevision"))
        assertEquals(
            "${metadata.getString("sourceVersion")}.${metadata.getInt("compatibilityRevision")}",
            metadata.getString("version"),
        )
        assertEquals("https://github.com/goastian/midori-tab", metadata.getString("sourceRepository"))
        val release = metadata.getJSONObject("release")
        assertEquals("v${metadata.getString("sourceVersion")}", release.getString("tag"))
        assertEquals(
            "https://github.com/goastian/midori-tab/releases/tag/${release.getString("tag")}",
            release.getString("url"),
        )
        assertEquals(
            "midori-tab-${metadata.getString("sourceVersion")}-firefox.zip",
            release.getJSONObject("firefoxAsset").getString("name"),
        )
        assertTrue(release.getJSONObject("firefoxAsset").getString("sha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(metadata.getString("bundleSha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(!metadata.has("sourceCommit"))
        assertTrue(!metadata.has("sourceRef"))
        assertTrue(manifest.getJSONArray("permissions").let { permissions ->
            (0 until permissions.length())
                .map(permissions::getString)
                .none { permission -> permission.contains("localhost") || permission.contains("127.0.0.1") }
        })
        assertTrue(asset("index.html").readText().contains("/index.js"))
        assertTrue(asset("index.js").length() > 0)
        val background = asset("background.js").readText()
        assertTrue(background.contains("chrome.windows?.onFocusChanged?.addListener"))
        assertTrue(background.contains("chrome.commands?.onCommand?.addListener"))
        assertTrue(background.contains("ANDROID_UNSUPPORTED_ACTIONS"))
        assertTrue(background.contains("chrome.history?.search"))
        assertTrue(background.contains("chrome.bookmarks?.search"))
        assertTrue(background.contains("'https://' + url"))
        assertTrue(asset("LICENSE.upstream").readText().contains("GNU AFFERO GENERAL PUBLIC LICENSE"))
    }

    @Test
    fun reinstallsOnlyWhenTheBundledExtensionVersionChanged() {
        assertTrue(MidoriNewTabFeature.needsReinstall("1.0.40.1", "1.0.40.2"))
        assertTrue(!MidoriNewTabFeature.needsReinstall("1.0.40.2", "1.0.40.2"))
        assertTrue(!MidoriNewTabFeature.needsReinstall("1.0.40.1", null))
    }

    private fun asset(path: String) = File("src/main/assets/extensions/midori_newtab", path)
}
