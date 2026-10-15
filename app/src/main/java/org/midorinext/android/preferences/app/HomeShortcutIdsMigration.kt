package org.midorinext.android.preferences.app

import androidx.datastore.core.DataMigration
import java.util.UUID

object HomeShortcutIdsMigration : DataMigration<AppPreferences> {
    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean {
        val ids = currentData.homeShortcutsList.map { it.id }
        return ids.any { it.isBlank() } || ids.distinct().size != ids.size
    }

    override suspend fun migrate(currentData: AppPreferences): AppPreferences {
        val reservedIds = currentData.homeShortcutsList.mapTo(mutableSetOf()) { it.id }
        val seenIds = mutableSetOf<String>()
        val shortcuts = currentData.homeShortcutsList.map { shortcut ->
            if (shortcut.id.isNotBlank() && seenIds.add(shortcut.id)) {
                shortcut
            } else {
                var id: String
                do {
                    id = UUID.randomUUID().toString()
                } while (!reservedIds.add(id))
                shortcut.toBuilder().setId(id).build()
            }
        }
        return currentData.toBuilder().clearHomeShortcuts().addAllHomeShortcuts(shortcuts).build()
    }

    override suspend fun cleanUp() = Unit
}
