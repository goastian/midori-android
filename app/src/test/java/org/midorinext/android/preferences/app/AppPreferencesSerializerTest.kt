package org.midorinext.android.preferences.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesSerializerTest {
    @Test
    fun newTabHomeIsShownByDefault() {
        assertFalse(AppPreferencesSerializer.defaultValue.openBlankNewTab)
    }

    @Test
    fun httpsOnlyIsEnabledForAllTabsByDefault() {
        assertEquals(
            HttpsOnlyLevel.ALL_TABS,
            AppPreferencesSerializer.defaultValue.httpsOnlyLevel,
        )
        assertTrue(AppPreferencesSerializer.defaultValue.httpsOnlyAllTabsMigrationCompleted)
    }

    @Test
    fun existingProfilesAreMigratedToHttpsOnlyOnce() = runBlocking {
        val existingPreferences = AppPreferences.newBuilder()
            .setHttpsOnlyLevel(HttpsOnlyLevel.OFF)
            .build()

        assertTrue(HttpsOnlyAllTabsMigration.shouldMigrate(existingPreferences))

        val migratedPreferences = HttpsOnlyAllTabsMigration.migrate(existingPreferences)
        assertEquals(HttpsOnlyLevel.ALL_TABS, migratedPreferences.httpsOnlyLevel)
        assertTrue(migratedPreferences.httpsOnlyAllTabsMigrationCompleted)
        assertFalse(HttpsOnlyAllTabsMigration.shouldMigrate(migratedPreferences))

        val disabledByUser = migratedPreferences.toBuilder()
            .setHttpsOnlyLevel(HttpsOnlyLevel.OFF)
            .build()
        assertFalse(HttpsOnlyAllTabsMigration.shouldMigrate(disabledByUser))
    }
}
