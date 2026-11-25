package org.midorinext.android.ui.browser.home

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShortcutUiStateTest {
    @Test
    fun rejectsIncompatibleSavedState() {
        assertNull(HomeShortcutUiState.Saver.restore(listOf(1, 0)))
        assertNull(HomeShortcutUiState.Saver.restore(listOf("collection")))
        assertNull(HomeShortcutUiState.Saver.restore(42))
        assertNull(HomeShortcutUiState.Saver.restore(listOf("collection", "folder-id", 1, 0)))
        assertNull(HomeShortcutUiState.Saver.restore(listOf(null, "folder-id")))
    }

    @Test
    fun restoresSavedShortcutStateWithoutRestoringTransientOperations() {
        val original = HomeShortcutUiState().apply {
            collectionId = "collection-id"
            pickerParentId = "picker-id"
            selectedId = "selected-id"
            editorId = "editor-id"
            editorParentId = "editor-parent-id"
            showEditor = true
            folderEditorId = "folder-editor-id"
            showFolderEditor = true
            movingId = "moving-id"
            removingFolderId = "removing-id"
            busy = true
            failed = true
            adInfoKey = "ad-key"
        }
        val saved = with(HomeShortcutUiState.Saver) {
            requireNotNull(SaverScope { true }.save(original))
        }
        val restored = requireNotNull(HomeShortcutUiState.Saver.restore(saved))

        assertEquals(original.collectionId, restored.collectionId)
        assertEquals(original.pickerParentId, restored.pickerParentId)
        assertEquals(original.selectedId, restored.selectedId)
        assertEquals(original.editorId, restored.editorId)
        assertEquals(original.editorParentId, restored.editorParentId)
        assertTrue(restored.showEditor)
        assertEquals(original.folderEditorId, restored.folderEditorId)
        assertTrue(restored.showFolderEditor)
        assertEquals(original.movingId, restored.movingId)
        assertEquals(original.removingFolderId, restored.removingFolderId)
        assertFalse(restored.busy)
        assertFalse(restored.failed)
        assertNull(restored.adInfoKey)
    }

    @Test
    fun restoresLegacyShortcutStateWithSafeDefaultsForInvalidFields() {
        val state = requireNotNull(HomeShortcutUiState.Saver.restore(listOf(
            "collection", "folder-id",
            "editorParent", "parent-id",
            "showEditor", true,
            "selected", 42,
            "showFolderEditor", "true",
        )))

        assertEquals("folder-id", state.collectionId)
        assertEquals("parent-id", state.editorParentId)
        assertTrue(state.showEditor)
        assertNull(state.selectedId)
        assertFalse(state.showFolderEditor)
    }
}
