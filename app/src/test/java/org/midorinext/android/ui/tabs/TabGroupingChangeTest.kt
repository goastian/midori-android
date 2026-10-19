package org.midorinext.android.ui.tabs

import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.preferences.app.SavedTabGroup

class TabGroupingChangeTest {
    private fun group(id: String, vararg tabs: String) = SavedTabGroup.newBuilder()
        .setId(id).setName(id).addAllTabIds(tabs.toList()).build()

    @Test
    fun creatingAGroupKeepsTargetFirstAndDoesNotChangeOtherGroups() {
        val original = listOf(group("other", "a", "b"))
        val change = changedTabGrouping(original, mapOf("other" to 4), "new", linkedSetOf("c", "d"), "Group", 2)!!
        assertEquals(original.single(), change.after.first())
        assertEquals(listOf("c", "d"), change.after.last().tabIdsList)
        assertEquals(mapOf("other" to 4, "new" to 2), change.afterColors)
        assertEquals(original to mapOf("other" to 4), change.undo(change.after, change.afterColors, setOf("a", "b", "c", "d")))
    }

    @Test
    fun movingTabsDissolvesAnUndersizedSourceAndUndoRestoresItsNameAndColor() {
        val before = listOf(group("source", "a", "b"), group("target", "c", "d"))
        val colors = mapOf("source" to 3, "target" to 5)
        val change = changedTabGrouping(before, colors, "target", setOf("a"), "ignored", 0)!!
        assertEquals(listOf("target"), change.after.map { it.id })
        assertEquals(listOf("c", "d", "a"), change.after.single().tabIdsList)
        assertEquals(mapOf("target" to 5), change.afterColors)
        assertEquals(before to colors, change.undo(change.after, change.afterColors, setOf("a", "b", "c", "d")))
    }

    @Test
    fun droppingExistingMembersOnTheirOwnGroupIsANoOp() {
        assertNull(changedTabGrouping(listOf(group("g", "a", "b")), emptyMap(), "g", setOf("a"), "Group", 0))
        assertNull(changedTabGrouping(emptyList(), emptyMap(), "g", setOf("a"), "Group", 0))
    }

    @Test
    fun undoPreservesAGroupCreatedAfterTheOriginalDrop() {
        val change = changedTabGrouping(emptyList(), emptyMap(), "first", setOf("a", "b"), "Group", 0)!!
        val later = group("later", "c", "d")
        val result = change.undo(change.after + later, change.afterColors + ("later" to 4), setOf("a", "b", "c", "d"))
        assertEquals(listOf(later), result.first)
        assertEquals(mapOf("later" to 4), result.second)
    }

    @Test
    fun anOldUndoDoesNotOverwriteLaterMembershipOrRenameChanges() {
        val change = changedTabGrouping(emptyList(), emptyMap(), "first", setOf("a", "b"), "Group", 0)!!
        val renamed = change.after.single().toBuilder().setName("Renamed").build()
        assertEquals(listOf(renamed) to change.afterColors,
            change.undo(listOf(renamed), change.afterColors, setOf("a", "b")))
        val enlarged = change.after.single().toBuilder().addTabIds("c").build()
        assertEquals(listOf(enlarged) to change.afterColors,
            change.undo(listOf(enlarged), change.afterColors, setOf("a", "b", "c")))
    }

    @Test
    fun undoDoesNotRestoreClosedTabs() {
        val before = listOf(group("source", "a", "b", "c"), group("target", "d", "e"))
        val colors = mapOf("source" to 1, "target" to 2)
        val change = changedTabGrouping(before, colors, "target", setOf("a"), "Group", 0)!!
        val current = change.after.map { it.toBuilder().clearTabIds().addAllTabIds(it.tabIdsList - "a").build() }
        val result = change.undo(current, change.afterColors, setOf("b", "c", "d", "e"))
        assertEquals(listOf(group("source", "b", "c"), before.last()), result.first)
        assertFalse(result.first.any { "a" in it.tabIdsList })
    }

    @Test
    fun ungroupingCanBeUndoneWithoutClosingAnyTabs() {
        val before = listOf(group("g", "a", "b"))
        val change = TabGroupingChange("g", before, emptyList(), mapOf("g" to 3), emptyMap())
        assertEquals(before to mapOf("g" to 3), change.undo(emptyList(), emptyMap(), setOf("a", "b")))
    }
}
