package org.midorinext.android.ui.nav

import android.os.Build
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import org.midorinext.android.ext.navigateSingleTopTo
import org.midorinext.android.ui.MidoriApplicationViewModel
import org.midorinext.android.ui.bookmarks.BookmarksScreen
import org.midorinext.android.ui.browser.BrowserScreen
import org.midorinext.android.ui.browser.TabOpening
import org.midorinext.android.ui.downloads.DownloadsScreen
import org.midorinext.android.ui.extensions.AddonDetailScreen
import org.midorinext.android.ui.extensions.ExtensionListScreen
import org.midorinext.android.ui.extensions.ExtensionViewModel
import org.midorinext.android.ui.history.HistoryScreen
import org.midorinext.android.pwa.WebAppsScreen
import org.midorinext.android.ui.preferences.AccessibilitySettingsScreen
import org.midorinext.android.ui.preferences.AutofillSettingsScreen
import org.midorinext.android.ui.preferences.CustomizeSettingsScreen
import org.midorinext.android.ui.preferences.DownloadSettingsScreen
import org.midorinext.android.ui.preferences.TranslationSettingsScreen
import org.midorinext.android.ui.preferences.HomepageSettingsScreen
import org.midorinext.android.ui.preferences.PasswordSettingsScreen
import org.midorinext.android.ui.preferences.NotificationSettingsScreen
import org.midorinext.android.ui.preferences.PreferencesScreen
import org.midorinext.android.ui.preferences.SearchSettingsScreen
import org.midorinext.android.ui.preferences.DefaultSearchEngineSettingsScreen
import org.midorinext.android.ui.preferences.AlternativeSearchEngineSettingsScreen
import org.midorinext.android.ui.preferences.SavedAutofillScreen
import org.midorinext.android.ui.preferences.SavedPasswordsScreen
import org.midorinext.android.ui.preferences.PrivacyScreen
import org.midorinext.android.ui.preferences.AdBlockSettingsScreen
import org.midorinext.android.ui.preferences.AdBlockSitesScreen
import org.midorinext.android.ui.preferences.AdBlockSourcesScreen
import org.midorinext.android.ui.tabs.TabsScreen
import org.midorinext.android.ui.theme.LocalMidoriTheme
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import org.midorinext.android.ui.animation.reduceMotionRequested
import org.midorinext.android.ui.animation.MidoriMotion

@Composable
fun MidoriNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
    onTabsPrivacyChanged: (Boolean) -> Unit = {},
) {
    val onBrowse = { navController.returnToBrowser() }
    val reduceMotion = reduceMotionRequested()
    val navigationOffset = with(LocalDensity.current) { MidoriMotion.NavigationOffset.roundToPx() } *
        if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
    val currentDestination by navController.currentBackStackEntryAsState()
    val browserUiState by appViewModel.browserUiState.collectAsStateWithLifecycle()
    val selectedTabIsPrivate = browserUiState?.isPrivate ?: LocalMidoriTheme.current.private

    // The browser surface is an AndroidView.  Keep the navigation host constrained to the
    // padded area provided by Scaffold so Gecko never measures against the full window and
    // renders underneath Midori's toolbar (particularly noticeable on large, older tablets).
    Box(modifier = modifier.fillMaxSize()) {
    NavHost(
        navController = navController,
        startDestination = NavDestination.Browser.match,
        modifier = Modifier.fillMaxSize(),
        enterTransition = {
            navigationEnterTransition(navigationOffset, reduceMotion)
        },
        exitTransition = {
            navigationExitTransition(-navigationOffset, reduceMotion)
        },
        popEnterTransition = {
            navigationEnterTransition(-navigationOffset, reduceMotion)
        },
        popExitTransition = {
            navigationExitTransition(navigationOffset, reduceMotion)
        },
        // Keep opaque native screens full size if a back gesture is interrupted by
        // another activity. Navigation's default predictive exit scales them to 70%.
        predictivePopEnterTransition = { EnterTransition.None },
        predictivePopExitTransition = { ExitTransition.None },
    ) {
        composable(
            route = NavDestination.Browser.match,
            arguments = NavDestination.Browser.arguments,
            enterTransition = { EnterTransition.None },
            popEnterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popExitTransition = { ExitTransition.None },
        ) { backStackEntry ->
            val openNewTab = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                backStackEntry.arguments?.getSerializable("openNewTab", TabOpening::class.java) ?: TabOpening.NONE
            } else {
                try {
                    @Suppress("DEPRECATION")
                    backStackEntry.arguments?.getSerializable("openNewTab") as TabOpening
                } catch (_: Exception) {
                    TabOpening.NONE
                }
            }
            MidoriBrowserTheme(
                darkTheme = LocalMidoriTheme.current.dark,
                privacy = selectedTabIsPrivate,
                updateSystemBars = false,
            ) {
                BrowserScreen(
                    navigateTo = { destination ->
                        if (destination == NavDestination.Tabs) {
                            navController.navigate(destination.route()) {
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigateSingleTopTo(destination.route())
                        }
                    },
                    appViewModel = appViewModel,
                    openNewTab = openNewTab
                )
            }
        }
        composable(
            route = NavDestination.Tabs.match,
            enterTransition = {
                if (reduceMotion) EnterTransition.None
                else fadeIn(tween(MidoriMotion.ReplacementDurationMillis))
            },
            exitTransition = {
                if (reduceMotion) ExitTransition.None
                else fadeOut(tween(MidoriMotion.ReplacementDurationMillis))
            },
            popEnterTransition = {
                if (reduceMotion) EnterTransition.None
                else fadeIn(tween(MidoriMotion.ReplacementDurationMillis))
            },
            popExitTransition = {
                if (reduceMotion) ExitTransition.None
                else fadeOut(tween(MidoriMotion.ReplacementDurationMillis))
            },
        ) {
            TabsScreen(
                appViewModel = appViewModel,
                isCurrentDestination = currentDestination?.destination?.route == NavDestination.Tabs.match,
                onPrivacyChanged = onTabsPrivacyChanged,
                onClose = {
                    if (!navController.popBackStack()) {
                        navController.navigateSingleTopTo(NavDestination.Browser.route())
                    }
                }
            )
        }
        composable(NavDestination.History.match) {
            HistoryScreen(onClose = onBrowse)
        }
        composable(NavDestination.Bookmarks.match) {
            BookmarksScreen(onBrowse)
        }
        composable(NavDestination.Downloads.match) {
            DownloadsScreen(onClose = onBrowse)
        }
        composable(NavDestination.WebApps.match) {
            WebAppsScreen()
        }
        composable(NavDestination.Preferences.match) {
            PreferencesScreen(
                onClose = onBrowse,
                navigateTo = { destination -> navController.navigate(destination.route()) },
                onNavigateToPrivacy = { navController.navigate(NavDestination.Privacy.route()) },
                applicationViewModel = appViewModel
            )
        }
        composable(NavDestination.SearchSettings.match) {
            SearchSettingsScreen(navigateTo = { navController.navigate(it.route()) })
        }
        composable(NavDestination.DefaultSearchEngineSettings.match) {
            DefaultSearchEngineSettingsScreen()
        }
        composable(NavDestination.AlternativeSearchEngineSettings.match) {
            AlternativeSearchEngineSettingsScreen()
        }
        composable(NavDestination.HomepageSettings.match) {
            HomepageSettingsScreen()
        }
        composable(NavDestination.CustomizeSettings.match) {
            CustomizeSettingsScreen()
        }
        composable(NavDestination.PasswordSettings.match) {
            PasswordSettingsScreen(
                navigateTo = { destination -> navController.navigate(destination.route()) },
            )
        }
        composable(NavDestination.AutofillSettings.match) {
            AutofillSettingsScreen(
                navigateTo = { destination -> navController.navigate(destination.route()) },
            )
        }
        composable(NavDestination.SavedPasswords.match) {
            SavedPasswordsScreen()
        }
        composable(NavDestination.SavedAutofill.match) {
            SavedAutofillScreen()
        }
        composable(NavDestination.AccessibilitySettings.match) {
            AccessibilitySettingsScreen()
        }
        composable(NavDestination.NotificationSettings.match) {
            NotificationSettingsScreen()
        }
        composable(NavDestination.DownloadSettings.match) {
            DownloadSettingsScreen()
        }
        composable(NavDestination.TranslationSettings.match) {
            TranslationSettingsScreen()
        }
        composable(NavDestination.Privacy.match) {
            PrivacyScreen(
                onClose = onBrowse,
            )
        }
        composable(NavDestination.AdBlockSettings.match) {
            AdBlockSettingsScreen(
                onSites = { navController.navigate(NavDestination.AdBlockSites.route()) },
                onTrackerSources = { navController.navigate(NavDestination.AdBlockTrackerSources.route()) },
                onAdSources = { navController.navigate(NavDestination.AdBlockAdSources.route()) },
            )
        }
        composable(NavDestination.AdBlockSites.match) { AdBlockSitesScreen() }
        composable(NavDestination.AdBlockTrackerSources.match) { AdBlockSourcesScreen(tracker = true) }
        composable(NavDestination.AdBlockAdSources.match) { AdBlockSourcesScreen(tracker = false) }
        navigation(
            startDestination = NavDestination.Extensions.match,
            route = "extensions_graph",
            enterTransition = {
                navigationEnterTransition(navigationOffset, reduceMotion, fullScreenSlide = true)
            },
            exitTransition = {
                navigationExitTransition(-navigationOffset, reduceMotion, fullScreenSlide = true)
            },
            popEnterTransition = {
                navigationEnterTransition(-navigationOffset, reduceMotion, fullScreenSlide = true)
            },
            popExitTransition = {
                navigationExitTransition(navigationOffset, reduceMotion, fullScreenSlide = true)
            },
        ) {
            composable(NavDestination.Extensions.match) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("extensions_graph")
                }
                val extensionViewModel: ExtensionViewModel = hiltViewModel(parentEntry)
                ExtensionListScreen(
                    onClose = onBrowse,
                    onAddonClick = { addonId ->
                        navController.navigate(NavDestination.AddonDetail.route(addonId))
                    },
                    viewModel = extensionViewModel
                )
            }
            composable(
                route = NavDestination.AddonDetail.match,
                arguments = NavDestination.AddonDetail.arguments
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("extensions_graph")
                }
                val extensionViewModel: ExtensionViewModel = hiltViewModel(parentEntry)
                val addonId = backStackEntry.arguments?.getString("addonId") ?: ""
                AddonDetailScreen(
                    addonId = addonId,
                    onClose = { navController.popBackStack() },
                    viewModel = extensionViewModel
                )
            }
        }
    }
    }
}
