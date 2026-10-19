package org.midorinext.android

import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.ext.defaultBrowserStatus
import org.midorinext.android.ui.preferences.DefaultBrowserPreference
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class DefaultBrowserCompatibilityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun missingRoleServiceFallsBackToSystemSettings() {
        val withoutRoleService = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? =
                if (name == "role") null else super.getSystemService(name)
        }

        val status = withoutRoleService.defaultBrowserStatus()

        assertFalse(status.isDefault)
        assertNull(status.roleRequestIntent)
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, status.requestIntent().action)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 28)
    fun android8And9NeverQueryTheRoleService() {
        val legacyContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? {
                check(name != "role") { "RoleManager does not exist before Android 10" }
                return super.getSystemService(name)
            }
        }

        val status = legacyContext.defaultBrowserStatus()

        assertNull(status.roleRequestIntent)
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, status.requestIntent().action)
    }

    @Test
    fun defaultBrowserPreferenceComposesOnSupportedAndroidVersions() {
        compose.setContent {
            MidoriBrowserTheme(darkTheme = false) {
                DefaultBrowserPreference()
            }
        }

        compose.onNodeWithText(context.getString(R.string.default_browser_label)).assertIsDisplayed()
    }
}
