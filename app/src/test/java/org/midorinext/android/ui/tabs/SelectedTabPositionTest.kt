package org.midorinext.android.ui.tabs

import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.preferences.app.TabsViewOption

class SelectedTabPositionTest {
    private val activeTabs = List(12) { createTab("https://example.org/active/$it") }
    private val inactiveTabs = List(4) { createTab("https://example.org/inactive/$it") }
    private val groups = List(2) { index ->
        SmartTabGroup(
            id = "group-$index",
            name = "Group $index",
            color = TabGroupColor.BLUE,
            tabs = List(8) { createTab("https://example.org/group/$index/$it") },
        )
    }

    @Test
    fun activeTabNearTheEndIncludesGridHeader() {
        val state = SmartTabsState(activeTabs = activeTabs)

        assertEquals(11, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.LIST))
        assertEquals(12, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.GRID))
    }

    @Test
    fun activeTabWithInactiveSectionIncludesListHeader() {
        val state = SmartTabsState(activeTabs = activeTabs, inactiveTabs = inactiveTabs)

        assertEquals(12, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.LIST))
        assertEquals(12, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.GRID))
    }

    @Test
    fun groupedTabRevealsItsGroupCard() {
        val state = SmartTabsState(groups = groups, activeTabs = activeTabs, inactiveTabs = inactiveTabs)

        for (view in listOf(TabsViewOption.LIST, TabsViewOption.GRID)) {
            assertEquals(1, state.selectedItemIndex(groups.last().tabs.last().id, view))
        }
    }

    @Test
    fun ungroupedTabAccountsForGroupCardsAndDifferentHeaders() {
        val state = SmartTabsState(groups = groups, activeTabs = activeTabs)

        assertEquals(14, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.LIST))
        assertEquals(13, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.GRID))
    }

    @Test
    fun inactiveTabAccountsForBothSectionHeaders() {
        val state = SmartTabsState(activeTabs = activeTabs, inactiveTabs = inactiveTabs)

        for (view in listOf(TabsViewOption.LIST, TabsViewOption.GRID)) {
            assertEquals(17, state.selectedItemIndex(inactiveTabs.last().id, view))
        }
    }

    @Test
    fun inactiveTabWithGroupsAccountsForDifferentHeaders() {
        val state = SmartTabsState(groups = groups, activeTabs = activeTabs, inactiveTabs = inactiveTabs)

        assertEquals(19, state.selectedItemIndex(inactiveTabs.last().id, TabsViewOption.LIST))
        assertEquals(18, state.selectedItemIndex(inactiveTabs.last().id, TabsViewOption.GRID))
    }

    @Test
    fun inactiveOnlyTrayIncludesItsHeader() {
        val state = SmartTabsState(inactiveTabs = inactiveTabs)

        for (view in listOf(TabsViewOption.LIST, TabsViewOption.GRID)) {
            assertEquals(4, state.selectedItemIndex(inactiveTabs.last().id, view))
        }
    }

    @Test
    fun privateTrayUsesOnlyPrivateTabPositions() {
        val privateTabs = List(8) { createTab("https://example.org/private/$it", private = true) }
        val state = SmartTabsState(activeTabs = activeTabs + privateTabs, groups = groups)
            .forPageAndQuery(TabsPage.PRIVATE, "")

        assertEquals(7, state.selectedItemIndex(privateTabs.last().id, TabsViewOption.LIST))
        assertEquals(8, state.selectedItemIndex(privateTabs.last().id, TabsViewOption.GRID))
        assertEquals(-1, state.selectedItemIndex(activeTabs.last().id, TabsViewOption.GRID))
    }

    @Test
    fun missingSelectionWaitsForTabsToLoad() {
        for (view in listOf(TabsViewOption.LIST, TabsViewOption.GRID)) {
            assertEquals(-1, SmartTabsState().selectedItemIndex(activeTabs.last().id, view))
            assertEquals(-1, SmartTabsState(activeTabs = activeTabs).selectedItemIndex(null, view))
            assertEquals(-1, SmartTabsState(activeTabs = activeTabs).selectedItemIndex("missing", view))
        }
    }
}
