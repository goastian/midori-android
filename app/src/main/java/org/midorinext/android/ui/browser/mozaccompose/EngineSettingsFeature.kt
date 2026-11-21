package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.midorinext.android.preferences.app.AppPreferences
import mozilla.components.concept.engine.Engine

@Composable
fun EngineSettingsFeature(
    engine: Engine,
    appPreferences: AppPreferences,
) {
    LaunchedEffect(
        appPreferences.accessibilityAutomaticFontSizing,
        appPreferences.accessibilityFontScale,
        appPreferences.accessibilityForceZoomEnabled,
        appPreferences.passwordAutofillEnabled,
    ) {
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
