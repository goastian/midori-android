/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components.appstate

import mozilla.components.feature.tab.collections.TabCollection
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.lib.crash.Crash.NativeCodeCrash
import mozilla.components.lib.state.Action
import org.midorinext.android.components.AppStore
import org.midorinext.android.home.Mode
import org.midorinext.android.home.recentbookmarks.RecentBookmark
import org.midorinext.android.home.recentsyncedtabs.RecentSyncedTabState
import org.midorinext.android.home.recenttabs.RecentTab
import org.midorinext.android.home.recentvisits.RecentlyVisitedItem
import org.midorinext.android.library.history.PendingDeletionHistory
import org.midorinext.android.home.recentsyncedtabs.RecentSyncedTab
import org.midorinext.android.wallpapers.Wallpaper

/**
 * [Action] implementation related to [AppStore].
 */
sealed class AppAction : Action {
    data class UpdateInactiveExpanded(val expanded: Boolean) : AppAction()

    /**
     * Updates whether the first frame of the homescreen has been [drawn].
     */
    data class UpdateFirstFrameDrawn(val drawn: Boolean) : AppAction()
    data class AddNonFatalCrash(val crash: NativeCodeCrash) : AppAction()
    data class RemoveNonFatalCrash(val crash: NativeCodeCrash) : AppAction()
    object RemoveAllNonFatalCrashes : AppAction()

    data class Change(
        val topSites: List<TopSite>,
        val mode: Mode,
        val collections: List<TabCollection>,
        val showCollectionPlaceholder: Boolean,
        val recentTabs: List<RecentTab>,
        val recentBookmarks: List<RecentBookmark>,
        val recentHistory: List<RecentlyVisitedItem>,
        val recentSyncedTabState: RecentSyncedTabState,
    ) :
        AppAction()

    data class CollectionExpanded(val collection: TabCollection, val expand: Boolean) :
        AppAction()

    data class CollectionsChange(val collections: List<TabCollection>) : AppAction()
    data class ModeChange(val mode: Mode) : AppAction()
    data class TopSitesChange(val topSites: List<TopSite>) : AppAction()
    data class RecentTabsChange(val recentTabs: List<RecentTab>) : AppAction()
    data class RemoveRecentTab(val recentTab: RecentTab) : AppAction()
    data class RecentBookmarksChange(val recentBookmarks: List<RecentBookmark>) : AppAction()
    data class RemoveRecentBookmark(val recentBookmark: RecentBookmark) : AppAction()
    data class RecentHistoryChange(val recentHistory: List<RecentlyVisitedItem>) : AppAction()
    data class RemoveRecentHistoryHighlight(val highlightUrl: String) : AppAction()
    data class DisbandSearchGroupAction(val searchTerm: String) : AppAction()

    /**
     * Adds a set of items marked for removal to the app state, to be hidden in the UI.
     */
    data class AddPendingDeletionSet(val historyItems: Set<PendingDeletionHistory>) : AppAction()
    /**
     * Removes a set of items, previously marked for removal, to be displayed again in the UI.
     */
    data class UndoPendingDeletionSet(val historyItems: Set<PendingDeletionHistory>) : AppAction()

    object RemoveCollectionsPlaceholder : AppAction()

    /**
     * Updates the [RecentSyncedTabState] with the given [state].
     */
    data class RecentSyncedTabStateChange(val state: RecentSyncedTabState) : AppAction()

    /**
     * Add a [RecentSyncedTab] url to the homescreen blocklist and remove it
     * from the recent synced tabs list.
     */
    data class RemoveRecentSyncedTab(val syncedTab: RecentSyncedTab) : AppAction()

    /**
     * [Action]s related to interactions with the wallpapers feature.
     */
    sealed class WallpaperAction : AppAction() {
        /**
         * Indicates that a different [wallpaper] was selected.
         */
        data class UpdateCurrentWallpaper(val wallpaper: Wallpaper) : WallpaperAction()

        /**
         * Indicates that the list of potential wallpapers has changed.
         */
        data class UpdateAvailableWallpapers(val wallpapers: List<Wallpaper>) : WallpaperAction()
    }

    /**
     * [AppAction] implementations related to the application lifecycle.
     */
    sealed class AppLifecycleAction : AppAction() {

        /**
         * The application has received an ON_RESUME event.
         */
        object ResumeAction : AppLifecycleAction()

        /**
         * The application has received an ON_PAUSE event.
         */
        object PauseAction : AppLifecycleAction()
    }
}
