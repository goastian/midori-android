package org.midorinext.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.R
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.preferences.app.HomepageOpeningScreen
import org.midorinext.android.ui.browser.TabOpening
import org.midorinext.android.ui.nav.NavDestination

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test
    fun browserRoutesCarryNewTabIntent() {
        assertEquals("browse?openNewTab=NONE", NavDestination.Browser.route())
        assertEquals("browse?openNewTab=NORMAL", NavDestination.Browser.route(TabOpening.NORMAL))
        assertEquals("browse?openNewTab=PRIVATE", NavDestination.Browser.route(TabOpening.PRIVATE))
    }

    @Test
    fun settingsRoutesExposePersonalizationSections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("settings/homepage", NavDestination.HomepageSettings.route())
        assertEquals("settings/customize", NavDestination.CustomizeSettings.route())
        assertEquals("settings/passwords", NavDestination.PasswordSettings.route())
        assertEquals("settings/autofill", NavDestination.AutofillSettings.route())
        assertEquals("settings/passwords/saved", NavDestination.SavedPasswords.route())
        assertEquals("settings/autofill/saved", NavDestination.SavedAutofill.route())
        assertEquals("settings/accessibility", NavDestination.AccessibilitySettings.route())
        assertEquals("New Tab", context.getString(R.string.settings_homepage_title))
        assertEquals("Customize", context.getString(R.string.settings_customize_title))
        assertEquals("Passwords", context.getString(R.string.settings_passwords_title))
        assertEquals("Autofill", context.getString(R.string.settings_autofill_title))
        assertEquals("Accessibility", context.getString(R.string.settings_accessibility_title))
    }

    @Test
    fun settingsDefaultsKeepTheNativeFallbackUsable() {
        val defaults = AppPreferencesSerializer.defaultValue

        assertFalse(defaults.openBlankNewTab)
        assertTrue(defaults.homepageShortcutsEnabled)
        assertTrue(defaults.homepageWeatherEnabled)
        assertTrue(defaults.homepageBackgroundPhotoEnabled)
        assertTrue(defaults.pullToRefreshEnabled)
        assertFalse(defaults.savePasswordsEnabled)
        assertFalse(defaults.passwordAutofillEnabled)
        assertFalse(defaults.autofillAddressesEnabled)
        assertFalse(defaults.accessibilityAutomaticFontSizing)
        assertEquals(100, defaults.accessibilityFontScale)
        assertFalse(defaults.accessibilityForceZoomEnabled)
        assertEquals(HomepageOpeningScreen.HOMEPAGE_AFTER_FOUR_HOURS, defaults.homepageOpeningScreen)
    }

    @Test
    fun officialMidoriNewTabBundleIsPackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("extensions/midori_newtab/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val metadata = context.assets.open("extensions/midori_newtab/upstream.json")
            .bufferedReader()
            .use { it.readText() }
        val index = context.assets.open("extensions/midori_newtab/index.html")
            .bufferedReader()
            .use { it.readText() }

        assertTrue(manifest.contains("midoritabs@astian.org"))
        assertFalse(manifest.contains("chrome_url_overrides"))
        assertFalse(manifest.contains("\"commands\""))
        assertTrue(metadata.contains("goastian/midori-tab"))
        assertTrue(metadata.contains("\"sourceVersion\""))
        assertTrue(metadata.contains("\"release\""))
        assertTrue(metadata.contains("\"firefoxAsset\""))
        assertTrue(index.contains("/index.js"))
    }

    @Test
    fun officialMidoriPrivacyBundleAndStatsBridgeArePackaged() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("extensions/midori_privacy/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val metadata = context.assets.open("extensions/midori_privacy/upstream.json")
            .bufferedReader()
            .use { it.readText() }
        val background = context.assets.open("extensions/midori_privacy/background.html")
            .bufferedReader()
            .use { it.readText() }
        val start = context.assets.open("extensions/midori_privacy/js/start.js")
            .bufferedReader()
            .use { it.readText() }
        val stats = context.assets.open("extensions/midori_privacy/js/midori-stats.js")
            .bufferedReader()
            .use { it.readText() }
        val manifestJson = JSONObject(manifest)
        val metadataJson = JSONObject(metadata)
        val permissions = manifestJson.getJSONArray("permissions").let { values ->
            List(values.length()) { index -> values.getString(index) }.toSet()
        }

        assertEquals(
            "midori-protection@astian.org",
            manifestJson
                .getJSONObject("browser_specific_settings")
                .getJSONObject("gecko")
                .getString("id"),
        )
        assertFalse(manifest.contains("midori-privacy@astian.org"))
        assertEquals(
            "popup-fenix.html",
            manifestJson.getJSONObject("browser_action").getString("default_popup"),
        )
        assertTrue(
            manifestJson
                .getJSONObject("browser_specific_settings")
                .getJSONObject("gecko_android")
                .getString("strict_min_version")
                .substringBefore('.')
                .toInt() >= 115,
        )
        assertTrue(permissions.containsAll(setOf("webRequest", "webRequestBlocking", "<all_urls>")))
        assertEquals(
            "https://github.com/goastian/midori-privacy",
            metadataJson.getString("sourceRepository"),
        )
        assertEquals(5, metadataJson.getInt("compatibilityRevision"))
        assertTrue(metadataJson.getJSONObject("release").has("firefoxAsset"))
        assertTrue(background.contains("src=\"js/start.js\""))
        assertTrue(start.contains("import './midori-stats.js';"))
        assertTrue(stats.contains("'midoritabs@astian.org'"))
        assertTrue(stats.contains("get-stats-summary"))
        assertTrue(stats.contains("schemaVersion: 2"))
        assertTrue(stats.contains("totalBlocked: blocked"))
        assertTrue(stats.contains("estimatedDataSavedBytes"))
        assertTrue(stats.contains("conservative-8kib-per-block-v1"))
    }
}
