package org.midorinext.android.preferences.app

import androidx.datastore.core.DataMigration

/** Enables HTTPS-only once for profiles created before it became Midori's default. */
object HttpsOnlyAllTabsMigration : DataMigration<AppPreferences> {
    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean =
        !currentData.httpsOnlyAllTabsMigrationCompleted

    override suspend fun migrate(currentData: AppPreferences): AppPreferences =
        currentData.toBuilder()
            .setHttpsOnlyLevel(HttpsOnlyLevel.ALL_TABS)
            .setHttpsOnlyAllTabsMigrationCompleted(true)
            .build()

    override suspend fun cleanUp() = Unit
}
