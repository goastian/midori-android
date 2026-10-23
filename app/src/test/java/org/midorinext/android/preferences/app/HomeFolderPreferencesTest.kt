package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.newtab.defaultHomeShortcuts

class HomeFolderPreferencesTest {
    @Test
    fun folderAndEditedChildrenPersistAndDissolvingFolderPreservesSites() = runBlocking {
        withRepository { repository, file ->
            assertTrue(repository.saveHomeFolder("folder", "  Research  "))
            assertTrue(repository.saveHomeShortcut("child", "Example", "example.org", "folder"))
            assertTrue(repository.moveHomeShortcutToFolder(defaultHomeShortcuts().first().id, "folder"))
            assertTrue(repository.saveHomeShortcut("child", "Updated", "https://example.org/updated"))
            assertTrue(repository.saveHomeFolder("folder", "Work"))

            val restored = AppPreferences.parseFrom(file.readBytes())
            val folder = restored.homeShortcutsList.single { it.id == "folder" }
            assertEquals("Work", folder.title)
            assertTrue(folder.isFolder)
            assertTrue(folder.parentId.isEmpty())
            assertTrue(folder.url.isEmpty())
            assertEquals("folder", restored.homeShortcutsList.single { it.id == "child" }.parentId)
            assertEquals("Updated", restored.homeShortcutsList.single { it.id == "child" }.title)

            repository.deleteHomeShortcut("folder")
            repository.ensureHomeShortcuts()
            val dissolved = AppPreferences.parseFrom(file.readBytes()).homeShortcutsList
            assertFalse(dissolved.any { it.id == "folder" })
            assertTrue(dissolved.all { it.parentId.isEmpty() })
            assertEquals(defaultHomeShortcuts().size + 1, dissolved.size)
            assertEquals("https://example.org/updated", dissolved.single { it.id == "child" }.url)
        }
    }

    @Test
    fun invalidDestinationsAndTypeChangesDoNotAlterStoredShortcuts() = runBlocking {
        withRepository { repository, file ->
            repository.saveHomeFolder("folder", "Work")
            repository.saveHomeFolder("other", "Other")
            repository.saveHomeShortcut("child", "Example", "example.org", "folder")
            val before = file.readBytes()

            assertFalse(repository.moveHomeShortcutToFolder("child", "missing"))
            assertFalse(repository.moveHomeShortcutToFolder("missing", "folder"))
            assertFalse(repository.moveHomeShortcutToFolder("folder", "other"))
            assertFalse(repository.moveHomeShortcutToFolder("folder", "folder"))
            assertFalse(repository.moveHomeShortcutToFolder("child", "child"))
            assertFalse(repository.saveHomeShortcut("folder", "Site", "example.org"))
            assertFalse(repository.saveHomeShortcut("new", "Site", "example.org", "missing"))
            assertFalse(repository.saveHomeShortcut("new", "Site", "javascript:alert(1)", "folder"))
            assertFalse(repository.saveHomeFolder("child", "Folder"))
            assertFalse(repository.saveHomeFolder(null, "  "))
            assertArrayEquals(before, file.readBytes())

            assertTrue(repository.moveHomeShortcutToFolder("child", ""))
            assertTrue(repository.flow.first().homeShortcutsList.single { it.id == "child" }.parentId.isEmpty())
        }
    }

    @Test
    fun orderingChangesOnlySiblingsAndMovingBetweenFoldersAppendsToDestination() = runBlocking {
        withRepository(emptyInitializedPreferences()) { repository, file ->
            repository.saveHomeShortcut("root-a", "A", "a.example")
            repository.saveHomeFolder("folder", "Work")
            repository.saveHomeShortcut("child-a", "Child A", "a.example", "folder")
            repository.saveHomeShortcut("root-b", "B", "b.example")
            repository.saveHomeShortcut("child-b", "Child B", "b.example", "folder")
            repository.saveHomeShortcut("root-c", "C", "c.example")

            repository.moveHomeShortcut("child-b", -1)
            var sites = AppPreferences.parseFrom(file.readBytes()).homeShortcutsList
            assertEquals(listOf("root-a", "folder", "root-b", "root-c"), sites.filter { it.parentId.isEmpty() }.map { it.id })
            assertEquals(listOf("child-b", "child-a"), sites.filter { it.parentId == "folder" }.map { it.id })

            repository.moveHomeShortcut("root-c", Int.MIN_VALUE)
            sites = repository.flow.first().homeShortcutsList
            assertEquals(listOf("root-c", "root-a", "folder", "root-b"), sites.filter { it.parentId.isEmpty() }.map { it.id })
            assertEquals(listOf("child-b", "child-a"), sites.filter { it.parentId == "folder" }.map { it.id })

            repository.moveHomeShortcutToFolder("root-a", "folder")
            sites = AppPreferences.parseFrom(file.readBytes()).homeShortcutsList
            assertEquals(listOf("child-b", "child-a", "root-a"), sites.filter { it.parentId == "folder" }.map { it.id })
            repository.moveHomeShortcut("child-b", Int.MAX_VALUE)
            assertEquals(listOf("child-a", "root-a", "child-b"), repository.flow.first().homeShortcutsList.filter { it.parentId == "folder" }.map { it.id })
        }
    }

    @Test
    fun firstMutationPreservesDefaultsAndInitializationPreservesExistingCustomSites() = runBlocking {
        val defaults = defaultHomeShortcuts()
        withRepository { repository, _ ->
            repository.saveHomeShortcut("custom", "Custom", "example.org")
            repository.ensureHomeShortcuts()
            assertEquals(defaults, repository.flow.first().homeShortcutsList.dropLast(1))
        }
        withRepository { repository, _ ->
            repository.deleteHomeShortcut(defaults.first().id)
            repository.ensureHomeShortcuts()
            assertEquals(defaults.drop(1), repository.flow.first().homeShortcutsList)
        }
        withRepository { repository, _ ->
            repository.moveHomeShortcut(defaults.last().id, -1)
            repository.ensureHomeShortcuts()
            assertEquals(defaults.size, repository.flow.first().homeShortcutsCount)
            assertEquals(defaults.last(), repository.flow.first().homeShortcutsList[defaults.lastIndex - 1])
        }
        val custom = HomeShortcut.newBuilder().setId("legacy").setTitle("Saved").setUrl("https://example.org").build()
        withRepository(AppPreferencesSerializer.defaultValue.toBuilder().addHomeShortcuts(custom).build()) { repository, _ ->
            repository.ensureHomeShortcuts()
            assertEquals(listOf(custom), repository.flow.first().homeShortcutsList)
        }
    }

    @Test
    fun categoryFolderAddsNormalizedUniqueSitesAtomically() = runBlocking {
        withRepository { repository, file ->
            val before = repository.flow.first()
            assertFalse(repository.addHomeShortcutFolder("Work", listOf("Valid" to "example.org", "Unsafe" to "javascript:alert(1)")))
            assertEquals(before, repository.flow.first())
            assertFalse(repository.addHomeShortcutFolder(" ", listOf("Valid" to "example.org")))
            assertTrue(repository.addHomeShortcutFolder("  Work  ", listOf(
                "First" to "example.org", "Duplicate" to "https://example.org", "Second" to "https://other.example",
            )))
            repository.ensureHomeShortcuts()

            val sites = AppPreferences.parseFrom(file.readBytes()).homeShortcutsList
            assertEquals(defaultHomeShortcuts(), sites.take(defaultHomeShortcuts().size))
            val folder = sites.single { it.isFolder }
            assertEquals("Work", folder.title)
            assertTrue(folder.parentId.isEmpty())
            val children = sites.filter { it.parentId == folder.id }
            assertEquals(listOf("First", "Second"), children.map { it.title })
            assertEquals(listOf("https://example.org", "https://other.example"), children.map { it.url })
            assertEquals(sites.size, sites.map { it.id }.distinct().size)
        }
    }

    @Test
    fun homeAppearanceSettingsPersistWithoutChangingBookmarksOrBrowserPreferences() = runBlocking {
        withRepository { repository, file ->
            repository.ensureHomeShortcuts()
            repository.updateOpenBlankNewTab(true)
            val original = repository.flow.first()
            assertEquals(HomeShortcutShape.HOME_SHORTCUT_ROUNDED, original.homeShortcutShape)
            assertFalse(original.homeLargeShortcuts)
            assertFalse(original.hideHomeAddShortcut)
            assertFalse(original.hideHomeLogo)
            assertFalse(original.hideHomeCustomizeShortcut)

            repository.updateHomeShortcutShape(HomeShortcutShape.HOME_SHORTCUT_CIRCLE)
            repository.updateHomeLargeShortcuts(true)
            repository.updateHomeAddShortcut(false)
            repository.updateHomeLogo(false)
            repository.updateHomeCustomizeShortcut(false)
            val restored = AppPreferences.parseFrom(file.readBytes())
            assertEquals(HomeShortcutShape.HOME_SHORTCUT_CIRCLE, restored.homeShortcutShape)
            assertTrue(restored.homeLargeShortcuts)
            assertTrue(restored.hideHomeAddShortcut)
            assertTrue(restored.hideHomeLogo)
            assertTrue(restored.hideHomeCustomizeShortcut)
            assertEquals(original, restored.toBuilder().clearHomeShortcutShape().clearHomeLargeShortcuts()
                .clearHideHomeAddShortcut().clearHideHomeLogo().clearHideHomeCustomizeShortcut().build())

            repository.updateHomeShortcutShape(HomeShortcutShape.HOME_SHORTCUT_SQUARE)
            assertEquals(HomeShortcutShape.HOME_SHORTCUT_SQUARE, AppPreferences.parseFrom(file.readBytes()).homeShortcutShape)
            repository.updateHomeShortcutShape(HomeShortcutShape.HOME_SHORTCUT_ROUNDED)
            repository.updateHomeLargeShortcuts(false)
            repository.updateHomeAddShortcut(true)
            repository.updateHomeLogo(true)
            repository.updateHomeCustomizeShortcut(true)
            assertEquals(original, repository.flow.first())
        }
    }

    private fun emptyInitializedPreferences(): AppPreferences = AppPreferencesSerializer.defaultValue.toBuilder()
        .setHomeShortcutsInitialized(true).build()

    private suspend fun withRepository(
        initial: AppPreferences? = null,
        block: suspend (AppPreferencesRepository, File) -> Unit,
    ) {
        val directory = kotlin.io.path.createTempDirectory("home-folders").toFile()
        val file = File(directory, "preferences.pb")
        if (initial != null) file.writeBytes(initial.toByteArray())
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val store = DataStoreFactory.create(serializer = AppPreferencesSerializer, scope = scope, produceFile = { file })
        try {
            block(AppPreferencesRepository(store), file)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
