package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.newtab.HomeSection
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.defaultHomeShortcuts

class HomePreferencesTest {
    @Test
    fun wallpaperPersistsAlongsideExistingPreferencesAndCanReturnToTheLegacyBackground() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("home-wallpaper").toFile()
        val file = File(directory, "preferences.pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(serializer = AppPreferencesSerializer, scope = scope, produceFile = { file })
        val repository = AppPreferencesRepository(store)
        try {
            repository.updateHomeSection(HomeSection.STORIES, false)
            repository.updateOpenBlankNewTab(true)
            val original = repository.flow.first()
            repository.updateHomeWallpaper(HomeWallpaper.FOREST)

            val restored = file.inputStream().use { AppPreferencesSerializer.readFrom(it) }
            assertEquals(HomeWallpaper.FOREST, HomeWallpaper.fromId(restored.homeWallpaperId))
            assertEquals(original, restored.toBuilder().clearHomeWallpaperId().build())

            repository.updateHomeWallpaper(HomeWallpaper.NONE)
            assertEquals(original, file.inputStream().use { AppPreferencesSerializer.readFrom(it) })
            assertEquals(HomeWallpaper.NONE, HomeWallpaper.fromId(original.homeWallpaperId))
            assertEquals(HomeWallpaper.NONE, HomeWallpaper.fromId("retired-wallpaper"))
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun editedShortcutOrderAndRemovalSurviveSerializationAndInitialization() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("home-preferences").toFile()
        val file = File(directory, "preferences.pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(serializer = AppPreferencesSerializer, scope = scope, produceFile = { file })
        val repository = AppPreferencesRepository(store)
        try {
            repository.ensureHomeShortcuts()
            val defaults = defaultHomeShortcuts()
            assertEquals(defaults, store.data.first().homeShortcutsList)
            assertTrue(repository.saveHomeShortcut(defaults.first().id, "Search", "example.org"))
            repository.ensureHomeShortcuts()
            assertEquals("Search", store.data.first().homeShortcutsList.first().title)
            assertFalse(repository.saveHomeShortcut(null, "Unsafe", "javascript:alert(1)"))
            repository.moveHomeShortcut(defaults.last().id, -defaults.lastIndex)
            assertEquals(defaults.last().id, AppPreferences.parseFrom(file.readBytes()).homeShortcutsList.first().id)
            store.data.first().homeShortcutsList.forEach { repository.deleteHomeShortcut(it.id) }
            repository.ensureHomeShortcuts()
            assertTrue(AppPreferences.parseFrom(file.readBytes()).homeShortcutsList.isEmpty())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun sectionCustomizationPreservesBlankTabAndOtherBrowserPreferences() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("home-sections").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(directory, "preferences.pb")
        val store = DataStoreFactory.create(serializer = AppPreferencesSerializer, scope = scope, produceFile = { file })
        val repository = AppPreferencesRepository(store)
        try {
            repository.updateOpenBlankNewTab(true)
            repository.updateHomeSection(HomeSection.STORIES, false)
            val restored = AppPreferences.parseFrom(file.readBytes())
            assertTrue(restored.openBlankNewTab)
            assertTrue(restored.hideHomeStories)
            assertFalse(restored.hideHomeShortcuts)
            assertEquals(ToolbarPosition.BOTTOM, restored.toolbarPosition)
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
