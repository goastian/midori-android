package org.midorinext.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        assertEquals("Homepage", context.getString(R.string.settings_homepage_title))
        assertEquals("Customize", context.getString(R.string.settings_customize_title))
        assertEquals("Passwords", context.getString(R.string.settings_passwords_title))
        assertEquals("Autofill", context.getString(R.string.settings_autofill_title))
        assertEquals("Accessibility", context.getString(R.string.settings_accessibility_title))
    }

    @Test
    fun settingsDefaultsMatchHomepageAndCustomizationExperience() {
        val defaults = AppPreferencesSerializer.defaultValue

        assertFalse(defaults.openBlankNewTab)
        assertTrue(defaults.homepageShortcutsEnabled)
        assertTrue(defaults.homepagePrivacyStatsEnabled)
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
    fun midoriPrivacyAssetsArePackagedWithoutBlockingBridge() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("extensions/midori_privacy/manifest.json")
            .bufferedReader()
            .use { it.readText() }
        val script = context.assets.open("extensions/midori_privacy/adblock_cosmetic.js")
            .bufferedReader()
            .use { it.readText() }

        assertTrue(manifest.contains("midori-privacy@astian.org"))
        assertTrue(manifest.contains("youtube.com"))
        assertFalse(manifest.contains("nativeMessaging"))
        assertFalse(script.contains("sendNativeMessage"))
        assertFalse(script.contains("connectNative"))
        assertFalse(script.contains("querySelectorAll"))
        assertFalse(script.contains("MutationObserver"))
    }
}
