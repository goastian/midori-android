package org.midorinext.android.ui.nav

import android.os.Build
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import org.midorinext.android.ui.preferences.AccessibilitySettingsScreen
import org.midorinext.android.ui.preferences.AutofillSettingsScreen
import org.midorinext.android.ui.preferences.CustomizeSettingsScreen
import org.midorinext.android.ui.preferences.HomepageSettingsScreen
import org.midorinext.android.ui.preferences.PasswordSettingsScreen
import org.midorinext.android.ui.preferences.PreferencesScreen
import org.midorinext.android.ui.preferences.SavedAutofillScreen
import org.midorinext.android.ui.preferences.SavedPasswordsScreen
import org.midorinext.android.ui.preferences.AppTrackingProtectionReportScreen
import org.midorinext.android.ui.preferences.PrivacyScreen
import org.midorinext.android.ui.readinglist.ReadingListScreen
import org.midorinext.android.ui.tabs.TabsScreen

@Composable
fun MidoriNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    appViewModel: MidoriApplicationViewModel = hiltViewModel(),
) {
    val onBrowse = { navController.navigateSingleTopTo(NavDestination.Browser.route()) }
    val transitionTimeMs = 250

    NavHost(
        navController = navController,
        startDestination = NavDestination.Browser.match,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(durationMillis = transitionTimeMs)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = transitionTimeMs)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = transitionTimeMs)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(durationMillis = transitionTimeMs)
            )
        }
    ) {
        composable(
            route = NavDestination.Browser.match,
            arguments = NavDestination.Browser.arguments,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = transitionTimeMs)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(durationMillis = transitionTimeMs)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = transitionTimeMs)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(durationMillis = transitionTimeMs)
                )
            }
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
            BrowserScreen(
                navigateTo = { destination -> navController.navigateSingleTopTo(destination.route()) },
                appViewModel = appViewModel,
                openNewTab = openNewTab
            )
        }
        composable(NavDestination.Tabs.match) {
            TabsScreen(
                appViewModel = appViewModel,
                onClose = { openNewTab ->
                    navController.navigateSingleTopTo(NavDestination.Browser.route(openNewTab) )
                }
            )
        }
        composable(NavDestination.History.match) {
            HistoryScreen(onClose = onBrowse)
        }
        composable(NavDestination.Bookmarks.match) {
            BookmarksScreen(onBrowse)
        }
        composable(NavDestination.ReadingList.match) {
            ReadingListScreen(onBrowse)
        }
        composable(NavDestination.Downloads.match) {
            DownloadsScreen(onClose = onBrowse)
        }
        composable(NavDestination.Preferences.match) {
            PreferencesScreen(
                onClose = onBrowse,
                navigateTo = { destination -> navController.navigate(destination.route()) },
                onNavigateToPrivacy = { navController.navigate(NavDestination.Privacy.route()) },
                applicationViewModel = appViewModel
            )
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
        composable(NavDestination.Privacy.match) {
            PrivacyScreen(
                onClose = onBrowse,
                onNavigateToAppTrackingReport = {
                    navController.navigate(NavDestination.AppTrackingReport.route())
                }
            )
        }
        composable(NavDestination.AppTrackingReport.match) {
            AppTrackingProtectionReportScreen()
        }
        navigation(
            startDestination = NavDestination.Extensions.match,
            route = "extensions_graph"
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
