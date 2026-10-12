package org.midorinext.android.ui.tabs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedTabsClosureTest {
    @Test
    fun normalSelectionClosesOnlyMatchingOpenNormalTabsAndKeepsTheirHistory() {
        val normal = createTab("https://example.org/normal", title = "Normal title")
        val other = createTab("https://example.org/other")
        val privateTab = createTab("https://example.org/private", private = true)
        val store = tabsTestStore(BrowserState(tabs = listOf(normal, other, privateTab), selectedTabId = other.id))
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        val closed = closeSelectedTabs(
            store, useCases, setOf(normal.id, privateTab.id, "missing"), false, history::remember,
        )

        assertEquals(1, closed)
        assertEquals(listOf(other.id, privateTab.id), store.state.tabs.map { it.id })
        assertEquals(other.id, store.state.selectedTabId)
        assertTrue(history.reopen())
        val reopened = store.state.tabs.single { it.content.url == normal.content.url }
        assertEquals("Normal title", reopened.content.title)
        assertFalse(reopened.content.private)
        assertFalse(history.reopen())
    }

    @Test
    fun privateSelectionCannotCloseNormalTabsOrEnterRecentlyClosed() {
        val normal = createTab("https://example.org/normal")
        val privateTabs = List(3) { createTab("https://example.org/private/$it", private = true) }
        val store = tabsTestStore(BrowserState(tabs = listOf(normal) + privateTabs, selectedTabId = normal.id))
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)
        val selectedIds = setOf(normal.id, privateTabs[0].id, privateTabs[1].id, "missing")

        val closed = closeSelectedTabs(store, useCases, selectedIds, true, history::remember)

        assertEquals(2, closed)
        assertEquals(listOf(normal.id, privateTabs.last().id), store.state.tabs.map { it.id })
        assertEquals(normal.id, store.state.selectedTabId)
        assertFalse(history.reopen())
    }

    @Test
    fun staleEmptyOrWrongCollectionSelectionKeepsPreviousUndoAvailable() {
        val normal = createTab("https://example.org/normal")
        val privateTab = createTab("https://example.org/private", private = true)
        val undoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = tabsTestStore(
                BrowserState(tabs = listOf(normal, privateTab), selectedTabId = normal.id),
                middleware = listOf(
                    UndoMiddleware(clearAfterMillis = 60_000, mainScope = undoScope, waitScope = undoScope),
                ),
            )
            val useCases = TabsUseCases(store)
            val history = RecentlyClosedTabs(useCases)
            useCases.removeTab(normal.id)
            val previousUndo = store.state.undoHistory

            for (selection in listOf(emptySet(), setOf(normal.id, "missing"), setOf(privateTab.id))) {
                assertEquals(0, closeSelectedTabs(store, useCases, selection, false, history::remember))
                assertEquals(listOf(privateTab.id), store.state.tabs.map { it.id })
                assertEquals(previousUndo, store.state.undoHistory)
                assertFalse(history.reopen())
            }

            useCases.undo()
            assertEquals(listOf(normal.id, privateTab.id), store.state.tabs.map { it.id })
        } finally {
            undoScope.cancel()
        }
    }
}
