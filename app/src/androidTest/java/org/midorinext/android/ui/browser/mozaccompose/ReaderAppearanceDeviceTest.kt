package org.midorinext.android.ui.browser.mozaccompose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.readerview.ReaderViewFeature
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.MidoriApplication
import org.midorinext.android.preferences.app.Appearance

class ReaderAppearanceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
        .applicationContext as MidoriApplication
    private val preferences get() = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    @After
    fun cleanUp() {
        preferences.edit().clear().commit()
    }

    @Test
    fun explicitAppThemesUpdateTheSameReaderAndRestoreItsSavedThemeInSystemMode() {
        preferences.edit()
            .putString("mozac-readerview-colorscheme", "SEPIA")
            .putString("mozac-readerview-fonttype", "SERIF")
            .putInt("mozac-readerview-fontsize", 5)
            .commit()
        val savedPreferences = preferences.all
        val context = object : ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences
        }
        val store = BrowserStore()
        var appearance by mutableStateOf(Appearance.DARK)
        lateinit var feature: ReaderViewFeature
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                feature = rememberReaderViewFeature(store, app.engine.get(), appearance)
            }
        }

        compose.runOnIdle {
            assertEquals(ReaderViewFeature.ColorScheme.DARK, effectiveColorScheme(feature))
            assertEquals(savedPreferences, preferences.all)
        }
        val originalFeature = feature
        compose.runOnIdle { appearance = Appearance.LIGHT }
        compose.runOnIdle {
            assertSame(originalFeature, feature)
            assertEquals(ReaderViewFeature.ColorScheme.LIGHT, effectiveColorScheme(feature))
            assertEquals(savedPreferences, preferences.all)
        }
        compose.runOnIdle { appearance = Appearance.SYSTEM_SETTINGS }
        compose.runOnIdle {
            assertSame(originalFeature, feature)
            assertEquals(ReaderViewFeature.ColorScheme.SEPIA, effectiveColorScheme(feature))
            assertEquals(savedPreferences, preferences.all)
        }
    }

    private fun effectiveColorScheme(feature: ReaderViewFeature): ReaderViewFeature.ColorScheme {
        val config = ReaderViewFeature::class.java.getDeclaredField("config").apply {
            isAccessible = true
        }.get(feature)
        return config.javaClass.getMethod("getColorScheme").invoke(config) as ReaderViewFeature.ColorScheme
    }

    private companion object {
        const val PREFERENCES = "midori-reader-appearance-test"
    }
}
