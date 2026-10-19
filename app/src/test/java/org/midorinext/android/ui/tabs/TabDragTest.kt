package org.midorinext.android.ui.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class TabDragTest {
    private val first = Rect(0f, 0f, 100f, 160f)
    private val second = Rect(120f, 0f, 220f, 160f)

    @Test
    fun aDropOnAnotherTabCreatesATargetWithoutAnyExistingGroups() {
        assertEquals(TabDropTarget.Tab("b"), tabDropTarget(second.center, setOf("a"), mapOf("a" to first, "b" to second), emptyMap(), emptyMap()))
        assertNull(tabDropTarget(first.center, setOf("a"), mapOf("a" to first), emptyMap(), emptyMap()))
    }

    @Test
    fun leavingTheHoveredCardClearsTheTargetAndMarginsDoNotGroupTabs() {
        val bounds = mapOf("b" to second)
        assertEquals(TabDropTarget.Tab("b"), tabDropTarget(second.center, setOf("a"), bounds, emptyMap(), emptyMap()))
        assertNull(tabDropTarget(Offset(300f, 200f), setOf("a"), bounds, emptyMap(), emptyMap()))
        assertNull(tabDropTarget(Offset(121f, 80f), setOf("a"), bounds, emptyMap(), emptyMap()))
    }

    @Test
    fun anExpandedGroupTakesPrecedenceOverItsChildrenButCannotTargetItself() {
        val tabs = mapOf("b" to second)
        val groups = mapOf("g" to Rect(0f, 0f, 240f, 200f))
        val members = mapOf("g" to setOf("b", "c"))
        assertEquals(TabDropTarget.Group("g"), tabDropTarget(second.center, setOf("a"), tabs, groups, members))
        assertNull(tabDropTarget(second.center, setOf("c"), tabs, groups, members))
    }

    @Test
    fun selectedMembersCanMoveAlongsideOtherTabsWithoutDuplicates() {
        assertEquals(TabDropTarget.Group("g"), tabDropTarget(second.center, setOf("a", "b"), emptyMap(),
            mapOf("g" to second), mapOf("g" to setOf("b", "c"))))
    }

    @Test
    fun longPressWithoutMovementOnlySelectsAndCancelClearsAllDragData() {
        val state = TabDragState()
        state.begin("a", setOf("a"), first)
        assertFalse(state.move(first.center))
        assertFalse(state.moving)
        assertTrue(state.move(second.center))
        assertTrue(state.moving)
        state.clear()
        assertNull(state.tabId)
        assertNull(state.position)
        assertTrue(state.movingIds.isEmpty())
        assertFalse(state.moving)
    }
}
