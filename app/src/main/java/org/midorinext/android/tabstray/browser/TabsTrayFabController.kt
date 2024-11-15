package org.midorinext.android.tabstray.browser

/**
 * Contract for handling all user interactions with the Tabs Tray floating action button.
 */
interface TabsTrayFabController {
    /**
     * Opens a new normal tab.
     */
    fun handleNormalTabsFabClick()

    /**
     * Opens a new private tab.
     */
    fun handlePrivateTabsFabClick()

    /**
     * Starts a re-sync of synced content if a sync isn't already underway.
     */
    fun handleSyncedTabsFabClick()
}
