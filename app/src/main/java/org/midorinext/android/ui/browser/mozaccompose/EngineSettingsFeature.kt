package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.AppPreferences
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme

@Composable
fun EngineSettingsFeature(
    engine: Engine,
    appPreferences: AppPreferences,
) {
    val appearance = appPreferences.appearance

    LaunchedEffect(
        appearance,
        appPreferences.accessibilityAutomaticFontSizing,
        appPreferences.accessibilityFontScale,
        appPreferences.accessibilityForceZoomEnabled,
        appPreferences.passwordAutofillEnabled,
    ) {
        engine.settings.preferredColorScheme = when (appearance) {
            Appearance.LIGHT -> PreferredColorScheme.Light
            Appearance.DARK -> PreferredColorScheme.Dark
            else -> PreferredColorScheme.System
        }
        engine.settings.loginAutofillEnabled = false
        engine.settings.forceUserScalableContent = appPreferences.accessibilityForceZoomEnabled
        engine.settings.automaticFontSizeAdjustment =
            appPreferences.accessibilityAutomaticFontSizing
        if (!appPreferences.accessibilityAutomaticFontSizing) {
            engine.settings.fontSizeFactor = appPreferences.accessibilityFontScale / 100f
            engine.settings.fontInflationEnabled = true
        }
    }
}
