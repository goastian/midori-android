package org.midorinext.android.ui.tabs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.UndoAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabClosureUndoTest {
    private val undoScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun cancelUndoScope() {
        undoScope.cancel()
    }

    @Test
    fun singlePrivateCloseCanBeUndoneWithoutEnteringRecentHistory() = runBlocking {
        val normal = createTab("https://example.org/normal")
        val privateTab = createTab("https://example.org/private", private = true)
        val store = storeWithUndo(listOf(normal, privateTab), privateTab.id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(listOf(privateTab))
        useCases.removeTab(privateTab.id)

        assertEquals(listOf(normal.id), store.state.tabs.map { it.id })
        assertEquals(privateTab.id, store.state.undoHistory.tabs.single().state.id)
        assertTrue(store.state.undoHistory.tabs.single().state.private)
        assertEquals(0, history.count.first())
        assertFalse(history.reopen())

        useCases.undo()

        val restored = store.state.tabs.single { it.id == privateTab.id }
        assertTrue(restored.content.private)
        assertEquals(privateTab.content.url, restored.content.url)
        assertEquals(privateTab.id, store.state.selectedTabId)
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertEquals(0, history.count.first())
        assertFalse(history.reopen())
    }

    @Test
    fun closingSeveralPrivateTabsKeepsUndoSeparateAndLeavesOtherTabsOpen() = runBlocking {
        val normal = createTab("https://example.org/normal")
        val privateTabs = (1..3).map { createTab("https://example.org/private/$it", private = true) }
        val store = storeWithUndo(listOf(normal) + privateTabs, privateTabs.first().id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)
        val closing = privateTabs.take(2)

        history.remember(closing)
        useCases.removeTabs(closing.map { it.id })

        assertEquals(listOf(normal.id, privateTabs.last().id), store.state.tabs.map { it.id })
        assertEquals(closing.map { it.id }, store.state.undoHistory.tabs.map { it.state.id })
        assertEquals(0, history.count.first())
        assertFalse(history.reopen())

        useCases.undo()

        assertEquals((listOf(normal) + privateTabs).map { it.id }, store.state.tabs.map { it.id })
        val restoredTabs = store.state.tabs.filter { it.id in closing.map { tab -> tab.id } }
        assertTrue(restoredTabs.all { it.content.private })
        assertEquals(closing.map { it.content.url }, restoredTabs.map { it.content.url })
        assertEquals(privateTabs.first().id, store.state.selectedTabId)
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertFalse(history.reopen())
    }

    @Test
    fun closingAllPrivateTabsDoesNotFeedNormalHistory() = runBlocking {
        val normal = createTab("https://example.org/normal")
        val privateTabs = (1..3).map { createTab("https://example.org/private/$it", private = true) }
        val store = storeWithUndo(listOf(normal) + privateTabs, privateTabs.last().id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(store.state.tabs.filter { it.content.private })
        useCases.removePrivateTabs()

        assertEquals(listOf(normal.id), store.state.tabs.map { it.id })
        assertEquals(privateTabs.map { it.id }, store.state.undoHistory.tabs.map { it.state.id })
        assertTrue(store.state.undoHistory.tabs.all { it.state.private })
        assertEquals(0, history.count.first())
        assertFalse(history.reopen())

        useCases.undo()

        assertEquals(
            privateTabs.map { it.content.url },
            store.state.tabs.filter { it.content.private }.map { it.content.url },
        )
        assertEquals(privateTabs.last().id, store.state.selectedTabId)
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertFalse(history.reopen())
    }

    @Test
    fun mixedBulkClosureOnlyReopensNormalTabsFromRecentHistory() = runBlocking {
        val tabs = listOf(
            createTab("https://example.org/normal/one"),
            createTab("https://example.org/private", private = true),
            createTab("https://example.org/normal/two"),
        )
        val store = storeWithUndo(tabs, tabs.first().id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(tabs)
        useCases.removeTabs(tabs.map { it.id })

        assertTrue(store.state.tabs.isEmpty())
        assertEquals(2, history.count.first())
        assertEquals(3, store.state.undoHistory.tabs.size)

        repeat(2) { assertTrue(history.reopen()) }
        assertEquals(
            tabs.filterNot { it.content.private }.map { it.content.url },
            store.state.tabs.map { it.content.url },
        )
        assertTrue(store.state.tabs.none { it.content.private })
        assertFalse(history.reopen())
    }

    @Test
    fun closingAllNormalTabsKeepsThemRecoverableAndLeavesPrivateTabsOpen() = runBlocking {
        val normalTabs = (1..2).map { createTab("https://example.org/normal/$it") }
        val privateTab = createTab("https://example.org/private", private = true)
        val store = storeWithUndo(normalTabs + privateTab, normalTabs.first().id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(store.state.tabs.filterNot { it.content.private })
        useCases.removeNormalTabs()

        assertEquals(listOf(privateTab.id), store.state.tabs.map { it.id })
        assertEquals(2, history.count.first())
        assertTrue(store.state.undoHistory.tabs.none { it.state.private })

        repeat(2) { assertTrue(history.reopen()) }
        assertEquals(
            normalTabs.map { it.content.url },
            store.state.tabs.filterNot { it.content.private }.map { it.content.url },
        )
        assertEquals(privateTab.id, store.state.tabs.single { it.content.private }.id)
        assertFalse(history.reopen())
    }

    @Test
    fun normalUndoDoesNotConsumeRecentlyClosedEntry() = runBlocking {
        val tab = createTab("https://example.org/normal", title = "Normal page")
        val store = storeWithUndo(listOf(tab), tab.id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(listOf(tab))
        useCases.removeTab(tab.id)
        useCases.undo()

        assertEquals(tab.id, store.state.tabs.single().id)
        assertFalse(store.state.tabs.single().content.private)
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertEquals(1, history.count.first())

        assertTrue(history.reopen())
        assertEquals(listOf(tab.content.url, tab.content.url), store.state.tabs.map { it.content.url })
        assertTrue(store.state.tabs.none { it.content.private })
        assertEquals(0, history.count.first())
    }

    @Test
    fun clearingPrivateUndoLeavesNoRecentlyClosedFallback() = runBlocking {
        val tab = createTab("https://example.org/private", private = true)
        val store = storeWithUndo(listOf(tab), tab.id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(listOf(tab))
        useCases.removeTab(tab.id)
        store.dispatch(UndoAction.ClearRecoverableTabs(store.state.undoHistory.tag))
        useCases.undo()

        assertTrue(store.state.tabs.isEmpty())
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertEquals(0, history.count.first())
        assertFalse(history.reopen())
    }

    @Test
    fun clearingNormalUndoStillAllowsRecentReopening() = runBlocking {
        val tab = createTab("https://example.org/normal")
        val store = storeWithUndo(listOf(tab), tab.id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(listOf(tab))
        useCases.removeTab(tab.id)
        store.dispatch(UndoAction.ClearRecoverableTabs(store.state.undoHistory.tag))
        useCases.undo()

        assertTrue(store.state.tabs.isEmpty())
        assertTrue(store.state.undoHistory.tabs.isEmpty())
        assertEquals(1, history.count.first())
        assertTrue(history.reopen())
        assertEquals(tab.content.url, store.state.tabs.single().content.url)
        assertFalse(store.state.tabs.single().content.private)
    }

    @Test
    fun privateUndoDoesNotReplaceEarlierNormalHistory() = runBlocking {
        val normal = createTab("https://example.org/normal")
        val privateTab = createTab("https://example.org/private", private = true)
        val store = storeWithUndo(listOf(normal, privateTab), privateTab.id)
        val useCases = TabsUseCases(store)
        val history = RecentlyClosedTabs(useCases)

        history.remember(listOf(normal))
        useCases.removeTab(normal.id)
        history.remember(listOf(privateTab))
        useCases.removeTab(privateTab.id)

        assertEquals(listOf(privateTab.id), store.state.undoHistory.tabs.map { it.state.id })
        assertEquals(1, history.count.first())

        useCases.undo()
        assertTrue(store.state.tabs.single().content.private)
        assertEquals(1, history.count.first())

        assertTrue(history.reopen())
        val reopenedNormal = store.state.tabs.single { !it.content.private }
        assertEquals(normal.content.url, reopenedNormal.content.url)
        assertEquals(0, history.count.first())
        assertTrue(store.state.undoHistory.tabs.isEmpty())
    }

    private fun storeWithUndo(tabs: List<TabSessionState>, selectedTabId: String): BrowserStore =
        tabsTestStore(
            initialState = BrowserState(tabs = tabs, selectedTabId = selectedTabId),
            middleware = listOf(
                UndoMiddleware(clearAfterMillis = 60_000, mainScope = undoScope, waitScope = undoScope),
            ),
        )
}
