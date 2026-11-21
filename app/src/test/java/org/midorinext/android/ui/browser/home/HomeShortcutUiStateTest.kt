package org.midorinext.android.ui.browser.home

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
