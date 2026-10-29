package org.midorinext.android.sync

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.R
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.theme.MidoriBrowserTheme

class SyncWelcomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun spanish_welcome_exposes_both_connection_methods() {
        var scans = 0
        var signIns = 0
        render(dark = false, onScan = { scans++ }, onSignIn = { signIns++ })
        compose.onNodeWithText("Escanear código QR").assertIsDisplayed().performClick()
        compose.onNodeWithText("Entrar con mi cuenta Astian").assertIsDisplayed().performClick()
        assertEquals(1, scans)
        assertEquals(1, signIns)
        screenshot("sync-welcome-light")
    }

    @Test fun english_welcome_supports_dark_mode() {
        render(dark = true, language = "en")
        compose.onNodeWithText("Scan QR code").assertIsDisplayed()
        compose.onNodeWithText("Sign in with my Astian account").assertIsDisplayed()
        screenshot("sync-welcome-dark")
    }

    @Test fun compact_screen_and_large_text_keep_errors_and_actions_accessible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val error = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es"))
        }).getString(R.string.sync_camera_denied)
        render(dark = false, largeText = true, busy = true, error = error)
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Escanear código QR").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("Entrar con mi cuenta Astian").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        screenshot("sync-welcome-large-text")
    }

    private fun render(
        dark: Boolean,
        language: String = "es",
        largeText: Boolean = false,
        busy: Boolean = false,
        error: String? = null,
        onScan: () -> Unit = {},
        onSignIn: () -> Unit = {},
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val localized = context.createConfigurationContext(config)
        compose.setContent {
            val density = LocalDensity.current
            MidoriBrowserTheme(darkTheme = dark) {
                CompositionLocalProvider(
                    LocalContext provides localized,
                    LocalConfiguration provides config,
                    LocalDensity provides Density(density.density, if (largeText) 1.6f else 1f),
                    LocalReduceMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Scaffold(modifier = Modifier.widthIn(max = if (largeText) 320.dp else 480.dp),
                            topBar = { SyncAppBar {} }) { padding ->
                            SyncWelcome(busy, error, onScan, onSignIn, Modifier.fillMaxSize().padding(padding))
                        }
                    }
                }
            }
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        File(compose.activity.filesDir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
