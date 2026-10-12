package org.midorinext.android.preferences.app

internal fun TabsViewOption.nextTabsViewOption(): TabsViewOption = when (this) {
    TabsViewOption.GRID, TabsViewOption.UNRECOGNIZED -> TabsViewOption.CAROUSEL
    TabsViewOption.CAROUSEL -> TabsViewOption.LIST
    TabsViewOption.LIST -> TabsViewOption.GRID
}
