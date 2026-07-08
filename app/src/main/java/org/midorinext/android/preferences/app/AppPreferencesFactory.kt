package org.midorinext.android.preferences.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile

object AppPreferencesFactory {
    private const val FILENAME = "prefs_app.pb"

    fun create(context: Context): DataStore<AppPreferences> {
        return DataStoreFactory.create(
            serializer = AppPreferencesSerializer,
            produceFile = { context.dataStoreFile(FILENAME) },
            migrations = listOf(AppPreferencesMigration.create(context))
        )
    }
}