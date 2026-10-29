package org.midorinext.android.ui

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import org.midorinext.android.ui.nav.MidoriNavHost
import org.midorinext.android.ui.nav.NavDestination
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import org.midorinext.android.ui.theme.darkColorScheme
import org.midorinext.android.ui.theme.lightColorScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.preferences.app.ToolbarPosition
import androidx.preference.PreferenceManager
import org.midorinext.android.R
import org.midorinext.android.ui.onboarding.MidoriOnboarding
import org.midorinext.android.ui.zap.ZapFeature
import org.midorinext.android.ui.extensions.ExtensionPrompt

@Composable
fun MidoriBrowserApp(
    applicationViewModel: MidoriApplicationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val onboardingKey = stringResource(R.string.pref_key_show_onboarding)
    val onboardingPreferences = remember(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
    }
    var showOnboarding by rememberSaveable {
        mutableStateOf(onboardingPreferences.getBoolean(onboardingKey, true))
    }

    val storedPreferences by applicationViewModel.preferencesSnapshot.collectAsStateWithLifecycle()
    val browserUiState by applicationViewModel.browserUiState.collectAsStateWithLifecycle()
    val systemTheme = isSystemInDarkTheme()
    val preferences = if (showOnboarding) storedPreferences else browserUiState?.preferences
    if (preferences == null) {
        val background = when (storedPreferences?.appearance) {
            null -> colorResource(R.color.startup_background)
            Appearance.LIGHT -> lightColorScheme.background
            Appearance.DARK -> darkColorScheme.background
            else -> if (systemTheme) darkColorScheme.background else lightColorScheme.background
        }
        Box(Modifier.fillMaxSize().background(background)
            .testTag("browser_startup_pending"))
        return
    }

    val navController = rememberNavController()
    val currentDestination by navController.currentBackStackEntryAsState()
    val trayIsVisible = currentDestination?.destination?.route == NavDestination.Tabs.match
    var trayPrivacy by remember(currentDestination?.id) { mutableStateOf<Boolean?>(null) }
    val isPrivate = browserUiState?.isPrivate == true
    val appearance = preferences.appearance
    val toolbarPosition = preferences.toolbarPosition
    val adBlockConfiguration by applicationViewModel.adBlockConfiguration.collectAsStateWithLifecycle()

    // Protobuf returns UNRECOGNIZED for enum values written by a newer or older app
    // version. Never hand that sentinel to Compose as a remember key: its generated
    // getNumber() implementation throws by design.
    val resolvedAppearance = when (appearance) {
        Appearance.LIGHT,
        Appearance.DARK,
        Appearance.SYSTEM_SETTINGS -> appearance
        else -> Appearance.SYSTEM_SETTINGS
    }
    val resolvedToolbarPosition = when (toolbarPosition) {
        ToolbarPosition.BOTTOM -> ToolbarPosition.BOTTOM
        else -> ToolbarPosition.TOP
    }

    val darkTheme by remember(resolvedAppearance, systemTheme) { derivedStateOf {
        when (resolvedAppearance) {
            Appearance.LIGHT -> false
            Appearance.DARK -> true
            Appearance.SYSTEM_SETTINGS -> systemTheme
            else -> false
        }
    } }

    MidoriBrowserTheme(
        darkTheme = darkTheme,
        privacy = if (trayIsVisible) trayPrivacy ?: isPrivate else isPrivate,
        updateSystemBars = !trayIsVisible,
    ) {
        if (showOnboarding) {
            val searchEngine = preferences.searchEngine.takeUnless { it == SearchEnginePreference.UNRECOGNIZED }
                ?: SearchEnginePreference.ASTIANGO
            MidoriOnboarding(
                onToolbarPositionSelected = applicationViewModel::updateToolbarPosition,
                blockingLevel = adBlockConfiguration.level,
                onBlockingLevelSelected = applicationViewModel::updateBlockingLevel,
                searchEngine = searchEngine,
                onSearchEngineSelected = applicationViewModel::updateSearchEngine,
                onComplete = {
                    onboardingPreferences.edit().putBoolean(onboardingKey, false).apply()
                    showOnboarding = false
                }
            )
        } else {
            Scaffold(
                modifier = Modifier.imePadding(),
                snackbarHost = {
                    SnackbarHost(
                        hostState = applicationViewModel.snackbarHostState,
                        modifier = Modifier.offset(
                            y = if (resolvedToolbarPosition == ToolbarPosition.BOTTOM) (-64).dp else 0.dp
                        )
                    )
                },
            ) { scaffoldPadding ->
                MidoriNavHost(
                    navController = navController,
                    appViewModel = applicationViewModel,
                    onTabsPrivacyChanged = { trayPrivacy = it },
                    modifier = Modifier.padding(scaffoldPadding)
                )
                ZapFeature(state = applicationViewModel.zapState)
                ExtensionPrompt()
            }
        }
    }
}
