package org.midorinext.android.ui.nav

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument
import org.midorinext.android.ui.browser.TabOpening


sealed class NavDestination(
    val match: String,
    val arguments: List<NamedNavArgument> = listOf()
) {
    open fun route() : String { return this.match }

    object Browser : NavDestination(
        match = "browse?openNewTab={openNewTab}",
        arguments = listOf(navArgument("openNewTab") {
            type = NavType.EnumType(TabOpening::class.java)
            defaultValue = TabOpening.NONE
        })
    ) {
        override fun route(): String { return this.route(TabOpening.NONE) }
        fun route(openNewTab: TabOpening): String {
            return "browse?openNewTab=${openNewTab}"
        }
    }
    object Tabs : NavDestination(match = "tabs")
    object Bookmarks : NavDestination(match = "bookmarks")
    object ReadingList : NavDestination(match = "reading_list")
    object Downloads : NavDestination(match = "downloads")
    object Preferences : NavDestination(match = "preferences")
    object HomepageSettings : NavDestination(match = "settings/homepage")
    object CustomizeSettings : NavDestination(match = "settings/customize")
    object PasswordSettings : NavDestination(match = "settings/passwords")
    object AutofillSettings : NavDestination(match = "settings/autofill")
    object SavedPasswords : NavDestination(match = "settings/passwords/saved")
    object SavedAutofill : NavDestination(match = "settings/autofill/saved")
    object AccessibilitySettings : NavDestination(match = "settings/accessibility")
    object Privacy : NavDestination(match = "privacy")
    object AppTrackingReport : NavDestination(match = "app_tracking_report")
    object History : NavDestination(match = "history")
    object Extensions : NavDestination(match = "extensions")
    object AddonDetail : NavDestination(
        match = "addon_detail/{addonId}",
        arguments = listOf(navArgument("addonId") { type = NavType.StringType })
    ) {
        fun route(addonId: String): String = "addon_detail/$addonId"
    }
}
