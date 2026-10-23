package org.midorinext.android.ui.nav

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import org.midorinext.android.ext.navigateSingleTopTo

internal fun NavHostController.returnToBrowser() {
    if (!popBackStack(graph.findStartDestination().id, inclusive = false)) {
        navigateSingleTopTo(NavDestination.Browser.route())
    }
}
