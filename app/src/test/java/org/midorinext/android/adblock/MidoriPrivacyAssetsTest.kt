package org.midorinext.android.adblock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MidoriPrivacyAssetsTest {
    @Test
    fun officialMidoriPrivacyBundleIsPackaged() {
        val manifest = JSONObject(asset("extensions/midori_privacy/manifest.json").readText())
        val gecko = manifest
            .getJSONObject("browser_specific_settings")
            .getJSONObject("gecko")
        val geckoAndroid = manifest
            .getJSONObject("browser_specific_settings")
            .getJSONObject("gecko_android")
        val background = asset("extensions/midori_privacy/background.html").readText()
        val backgroundScript = asset("extensions/midori_privacy/js/background.js").readText()
        val assetsScript = asset("extensions/midori_privacy/js/assets.js").readText()
        val vapiCommonScript = asset("extensions/midori_privacy/js/vapi-common.js").readText()
        val start = asset("extensions/midori_privacy/js/start.js").readText()
        val storageScript = asset("extensions/midori_privacy/js/storage.js").readText()
        val stats = asset("extensions/midori_privacy/js/midori-stats.js").readText()
        val metadata = JSONObject(asset("extensions/midori_privacy/upstream.json").readText())
        val permissions = manifest.getJSONArray("permissions").let { values ->
            List(values.length()) { index -> values.getString(index) }.toSet()
        }

        assertEquals(2, manifest.getInt("manifest_version"))
        assertTrue(manifest.getString("version").matches(Regex("\\d+\\.\\d+\\.\\d+\\.5")))
        assertEquals("midori-protection@astian.org", gecko.getString("id"))
        assertTrue(gecko.getString("strict_min_version").substringBefore('.').toInt() >= 115)
        assertTrue(geckoAndroid.getString("strict_min_version").substringBefore('.').toInt() >= 115)
        assertEquals(
            "popup-fenix.html",
            manifest.getJSONObject("browser_action").getString("default_popup"),
        )
        assertEquals("background.html", manifest.getJSONObject("background").getString("page"))
        assertEquals(
            setOf("webRequest", "webRequestBlocking", "<all_urls>"),
            permissions.intersect(setOf("webRequest", "webRequestBlocking", "<all_urls>")),
        )
        assertTrue(background.contains("src=\"js/start.js\""))
        assertTrue(backgroundScript.contains("assetsJsonPath: 'assets/assets.json'"))
        assertFalse(backgroundScript.contains("assets/assets.dev.json"))
        assertTrue(vapiCommonScript.contains("const isAndroidCompatibilityBuild ="))
        assertTrue(vapiCommonScript.contains("isAndroidCompatibilityBuild === false"))
        assertTrue(assetsScript.contains("function assetSourceRegistryIsValid(registry)"))
        assertTrue(assetsScript.contains("registry['assets.json'] instanceof Object === false"))
        assertTrue(assetsScript.contains("assetKey !== 'user-filters'"))
        assertTrue(assetsScript.contains("entry.submitter !== 'user'"))
        assertTrue(assetsScript.contains("if ( assetSourceRegistryIsValid(cachedRegistry) )"))
        assertTrue(assetsScript.contains("if ( assetSourceRegistryIsValid(newDict) === false ) { return; }"))
        assertTrue(assetsScript.contains("assets.fetch = function(url, options = {})"))
        assertFalse(assetsScript.contains("Midori Privacy could not fetch packaged asset"))
        assertTrue(start.contains("import './midori-stats.js';"))
        assertTrue(storageScript.contains("midoriAndroidFilterListsMigration"))
        assertTrue(storageScript.contains("hasBrokenAndroidBootstrapState"))
        assertTrue(storageScript.contains("availableListKeys.every(key => key === this.userFiltersPath)"))
        assertTrue(storageScript.contains("this.selfieManager.destroy();"))
        assertTrue(stats.contains("const STATS_ACTION = 'get-stats-summary';"))
        assertTrue(stats.contains("'midoritabs@astian.org'"))
        assertTrue(stats.contains("ALLOWED_SENDER_IDS.has(sender.id) === false"))
        assertTrue(stats.contains("schemaVersion: 2"))
        assertTrue(stats.contains("totalBlocked: blocked"))
        assertTrue(stats.contains("totalRequests: blocked + allowed"))
        assertTrue(stats.contains("estimatedDataSavedBytes,"))
        assertTrue(stats.contains("dataSavedEstimateModel: 'conservative-8kib-per-block-v1'"))
        assertEquals("https://github.com/goastian/midori-privacy", metadata.getString("sourceRepository"))
        assertEquals(manifest.getString("version"), metadata.getString("version"))
        assertEquals(5, metadata.getInt("compatibilityRevision"))
        assertTrue(metadata.getString("bundleSha256").matches(Regex("[0-9a-f]{64}")))
        assertTrue(!metadata.has("sourceCommit"))
        assertTrue(!metadata.has("sourceRef"))
        assertTrue(
            metadata
                .getJSONObject("release")
                .getJSONObject("firefoxAsset")
                .getString("sha256")
                .matches(Regex("[0-9a-f]{64}")),
        )
        assertTrue(asset("extensions/midori_privacy/popup-fenix.html").isFile)
        assertTrue(asset("extensions/midori_privacy/LICENSE.upstream").isFile)
        assertFalse(manifest.toString().contains("midori-privacy@astian.org"))
    }

    @Test
    fun privacyExtensionIdentityRetainsTheLegacyMigrationTarget() {
        assertEquals("midori-protection@astian.org", MidoriPrivacyFeature.EXTENSION_ID)
        assertEquals("midori-privacy@astian.org", MidoriPrivacyFeature.LEGACY_EXTENSION_ID)
        assertEquals(
            setOf(
                "midori-privacy@astian.org",
                "easy-adblocker@easybrowser.local",
                "midori-vip-android@astian.org",
                "qwant-vip-android@qwant.com",
            ),
            MidoriPrivacyFeature.LEGACY_EXTENSION_IDS,
        )
    }

    @Test
    fun changedBundledPrivacyVersionTriggersReinstall() {
        assertTrue(MidoriPrivacyFeature.needsReinstall("2.3.11", "2.3.11.5"))
        assertTrue(MidoriPrivacyFeature.needsReinstall(null, "2.3.11.5"))
        assertFalse(MidoriPrivacyFeature.needsReinstall("2.3.11.5", "2.3.11.5"))
        assertFalse(MidoriPrivacyFeature.needsReinstall("2.3.11", null))
    }

    private fun asset(path: String) = File("src/main/assets", path)
}
