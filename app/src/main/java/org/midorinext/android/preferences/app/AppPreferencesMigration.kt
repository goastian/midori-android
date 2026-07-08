package org.midorinext.android.preferences.app

import android.content.Context
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import mozilla.components.concept.engine.Engine

object AppPreferencesMigration {
    private const val SHARED_PREFS_NAME = "app_preferences"
    private val keysToMigrate = setOf(
        "pref_key_general_launchexternalapp",
        "pref_key_privacy_cleardata_on_close",
        "pref_key_cleardata_content_history",
        "pref_key_cleardata_content_tabs",
        "pref_key_cleardata_content_browsingdata",
    )

    fun create(context: Context) : SharedPreferencesMigration<AppPreferences> {
        return SharedPreferencesMigration(
            context, SHARED_PREFS_NAME, keysToMigrate
        ) { sharedPrefs: SharedPreferencesView, currentData: AppPreferences ->
            with (currentData.toBuilder()) {
                openLinksInApp = sharedPrefs.getBoolean("pref_key_general_launchexternalapp", true)
                clearDataOnQuit = sharedPrefs.getBoolean("pref_key_privacy_cleardata_on_close", true)
                clearDataHistory = sharedPrefs.getBoolean("pref_key_cleardata_content_history", true)
                clearDataTabs = sharedPrefs.getBoolean("pref_key_cleardata_content_tabs", true)
                clearDataBrowsingdata = sharedPrefs.getInt("pref_key_cleardata_content_browsingdata", Engine.BrowsingData.ALL)
                build()
            }
        }
    }
}