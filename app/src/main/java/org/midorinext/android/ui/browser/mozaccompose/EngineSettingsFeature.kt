package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.ui.MidoriApplicationViewModel
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme

@Composable
fun EngineSettingsFeature(
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    engine: Engine,
    appPreferences: AppPreferences,
) {
    val appearance by appViewModel.appearance.collectAsState()

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
        engine.settings.loginAutofillEnabled = appPreferences.passwordAutofillEnabled
        engine.settings.forceUserScalableContent = appPreferences.accessibilityForceZoomEnabled
        engine.settings.automaticFontSizeAdjustment =
            appPreferences.accessibilityAutomaticFontSizing
        if (!appPreferences.accessibilityAutomaticFontSizing) {
            engine.settings.fontSizeFactor = appPreferences.accessibilityFontScale / 100f
            engine.settings.fontInflationEnabled = true
        }
    }
}
