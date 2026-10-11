package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.newtab.HomeSection

class HomePreferencesTest {
    @Test
    fun editedShortcutOrderAndRemovalSurviveSerializationAndInitialization() = runBlocking {
        val directory = kotlin.io.path.createTempDirectory("home-preferences").toFile()
        val file = File(directory, "preferences.pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = DataStoreFactory.create(serializer = AppPreferencesSerializer, scope = scope, produceFile = { file })
        val repository = AppPreferencesRepository(store)
        try {
            repository.ensureHomeShortcuts()
            assertEquals(listOf("google", "wikipedia", "midori"), store.data.first().homeShortcutsList.map { it.id })
            assertTrue(repository.saveHomeShortcut("google", "Search", "example.org"))
            repository.ensureHomeShortcuts()
            assertEquals("Search", store.data.first().homeShortcutsList.first().title)
            assertFalse(repository.saveHomeShortcut(null, "Unsafe", "javascript:alert(1)"))
            repository.moveHomeShortcut("midori", -2)
            assertEquals("midori", AppPreferences.parseFrom(file.readBytes()).homeShortcutsList.first().id)
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
