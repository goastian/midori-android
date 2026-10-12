package org.midorinext.android.ui.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TabsUiStateTest {
    @Test
    fun changingCollectionClearsSearchInBothDirections() {
        for (page in TabsPage.entries) {
            val state = TabsUiState(page = page, mode = TabsMode.Searching("personal query"))
            val destination = TabsPage.entries.first { it != page }

            val result = state.reduce(TabsUiAction.SelectPage(destination))

            assertEquals(destination, result.page)
            assertEquals(TabsMode.Browsing, result.mode)
        }
    }

    @Test
    fun changingCollectionCancelsSelectionAndItsTargetGroup() {
        val state = TabsUiState(
            page = TabsPage.NORMAL,
            mode = TabsMode.Selecting(setOf("first", "second"), targetGroupId = "group"),
        )

        val result = state.reduce(TabsUiAction.SelectPage(TabsPage.PRIVATE))

        assertEquals(TabsPage.PRIVATE, result.page)
        assertEquals(TabsMode.Browsing, result.mode)
    }

    @Test
    fun selectingCurrentCollectionKeepsTheCurrentInteraction() {
        for (mode in listOf(TabsMode.Searching("query"), TabsMode.Selecting(setOf("first")))) {
            val state = TabsUiState(page = TabsPage.NORMAL, mode = mode)

            assertSame(state, state.reduce(TabsUiAction.SelectPage(TabsPage.NORMAL)))
        }
    }

    @Test
    fun exitingSearchOrSelectionKeepsTheCollection() {
        for (page in TabsPage.entries) {
            for (mode in listOf(TabsMode.Searching("query"), TabsMode.Selecting(setOf("first")))) {
                val result = TabsUiState(page, mode).reduce(TabsUiAction.ExitTransientMode)

                assertEquals(TabsUiState(page, TabsMode.Browsing), result)
            }
        }
    }

    @Test
    fun delayedSearchUpdatesCannotRestoreQueryAfterChangingCollection() {
        val state = TabsUiState(TabsPage.PRIVATE, TabsMode.Searching("private query"))
            .reduce(TabsUiAction.SelectPage(TabsPage.NORMAL))
            .reduce(TabsUiAction.UpdateSearch("private query updated"))

        assertEquals(TabsUiState(TabsPage.NORMAL), state)
    }
}
