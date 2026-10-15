package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.newtab.defaultHomeShortcuts

class HomeShortcutIdsMigrationTest {
    @Test
    fun defaultShortcutsHaveUniqueNonblankIds() {
        val shortcuts = defaultHomeShortcuts()
        assertTrue(shortcuts.isNotEmpty())
        assertTrue(shortcuts.all { it.id.isNotBlank() })
        assertEquals(shortcuts.size, shortcuts.map { it.id }.distinct().size)
    }

    @Test
    fun repairsRepeatedIdsWithoutChangingLinksOrderOrOtherPreferences() = runBlocking {
        val original = legacyPreferences()
        assertTrue(HomeShortcutIdsMigration.shouldMigrate(original))

        val migrated = HomeShortcutIdsMigration.migrate(original)
        val shortcuts = migrated.homeShortcutsList
        assertEquals(shortcuts.size, shortcuts.map { it.id }.distinct().size)
        assertEquals("kayak", shortcuts.first().id)
        assertEquals("custom", shortcuts.last().id)
        assertEquals(
            original.homeShortcutsList.map { it.toBuilder().clearId().build() },
            shortcuts.map { it.toBuilder().clearId().build() },
        )
        assertEquals(original.toBuilder().clearHomeShortcuts().build(), migrated.toBuilder().clearHomeShortcuts().build())
        assertFalse(HomeShortcutIdsMigration.shouldMigrate(migrated))
        assertEquals(migrated, HomeShortcutIdsMigration.migrate(migrated))
    }

    @Test
    fun repairsBlankIdsAndPreservesExistingUniqueIds() = runBlocking {
        val original = AppPreferences.newBuilder().addAllHomeShortcuts(listOf(
            shortcut("", "First", "https://example.org/first"),
            shortcut(" ", "Second", "https://example.org/second"),
            shortcut("custom", "Third", "https://example.org/third"),
        )).build()
        assertTrue(HomeShortcutIdsMigration.shouldMigrate(original))

        val migrated = HomeShortcutIdsMigration.migrate(original)
        assertTrue(migrated.homeShortcutsList.all { it.id.isNotBlank() })
        assertEquals(3, migrated.homeShortcutsList.map { it.id }.distinct().size)
        assertEquals(original.homeShortcutsList.last(), migrated.homeShortcutsList.last())
        assertFalse(migrated.homeShortcutsInitialized)
        assertFalse(HomeShortcutIdsMigration.shouldMigrate(migrated))
    }

    @Test
    fun emptyProfilesAndRemovedShortcutsStayEmpty() = runBlocking {
        for (initialized in listOf(false, true)) {
            val original = AppPreferences.newBuilder().setHomeShortcutsInitialized(initialized).build()
            assertFalse(HomeShortcutIdsMigration.shouldMigrate(original))
            assertEquals(original, HomeShortcutIdsMigration.migrate(original))
        }
    }

    @Test
    fun dataStorePersistsRepairsBeforeEmittingAndShortcutsCanBeEditedIndependently() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("home-shortcut-ids").toFile()
        val file = File(directory, "preferences.pb")
        file.writeBytes(legacyPreferences().toByteArray())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(
            serializer = AppPreferencesSerializer,
            migrations = listOf(HomeShortcutIdsMigration),
            scope = scope,
            produceFile = { file },
        )
        val repository = AppPreferencesRepository(store)
        try {
            val migrated = store.data.first()
            assertFalse(HomeShortcutIdsMigration.shouldMigrate(migrated))
            assertEquals(migrated, AppPreferences.parseFrom(file.readBytes()))
            repository.ensureHomeShortcuts()
            assertEquals(migrated, store.data.first())

            val hotels = migrated.homeShortcutsList[1]
            assertTrue(repository.saveHomeShortcut(hotels.id, "My hotels", hotels.url))
            assertEquals("Kayak", store.data.first().homeShortcutsList.first().title)
            assertEquals("My hotels", store.data.first().homeShortcutsList[1].title)
            repository.moveHomeShortcut(hotels.id, 1)
            assertEquals(hotels.id, store.data.first().homeShortcutsList[2].id)
            repository.deleteHomeShortcut(hotels.id)
            val remaining = store.data.first().homeShortcutsList
            assertEquals(listOf("Kayak", "Cars", "Custom"), remaining.map { it.title })
            assertEquals(remaining, AppPreferences.parseFrom(file.readBytes()).homeShortcutsList)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    private fun legacyPreferences(): AppPreferences = AppPreferences.newBuilder()
        .setHomeShortcutsInitialized(true)
        .setHideHomeStories(true)
        .setToolbarPosition(ToolbarPosition.TOP)
        .addAllHomeShortcuts(listOf(
            shortcut("kayak", "Kayak", "https://www.kayak.com/flights"),
            shortcut("kayak", "Hotels", "https://www.kayak.com/hotels"),
            shortcut("kayak", "Cars", "https://www.kayak.com/cars"),
            shortcut("custom", "Custom", "https://example.org/"),
        ))
        .build()

    private fun shortcut(id: String, title: String, url: String): HomeShortcut = HomeShortcut.newBuilder()
        .setId(id).setTitle(title).setUrl(url).build()
}
