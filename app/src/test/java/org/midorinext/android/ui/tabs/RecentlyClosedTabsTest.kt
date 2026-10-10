package org.midorinext.android.ui.tabs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentlyClosedTabsTest {
    private val store = tabsTestStore()
    private val tabsUseCases = TabsUseCases(store)
    private val history = RecentlyClosedTabs(tabsUseCases)

    @Test
    fun ignoresSinglePrivateTab() = runBlocking {
        history.remember(listOf(createTab("https://example.org/private", private = true)))

        assertEquals(0, history.count.first())
        assertFalse(history.reopen())
        assertTrue(store.state.tabs.isEmpty())
    }

    @Test
    fun ignoresSeveralPrivateTabs() = runBlocking {
        history.remember(
            listOf(
                createTab("https://example.org/private/one", private = true),
                createTab("https://example.org/private/two", private = true),
            ),
        )

        assertEquals(0, history.count.first())
        assertFalse(history.reopen())
        assertTrue(store.state.tabs.isEmpty())
    }

    @Test
    fun keepsOnlyNormalTabsFromMixedBatch() = runBlocking {
        history.remember(
            listOf(
                createTab("https://example.org/private/one", private = true),
                createTab("https://example.org/normal", title = "Normal page"),
                createTab("https://example.org/private/two", private = true),
            ),
        )

        assertEquals(1, history.count.first())
        assertTrue(history.reopen())
        val reopened = store.state.tabs.single()
        assertEquals("https://example.org/normal", reopened.content.url)
        assertEquals("Normal page", reopened.content.title)
        assertFalse(reopened.content.private)
        assertFalse(history.reopen())
    }

    @Test
    fun reopeningClosedNormalTabRestoresTitleAndSelectsNewNormalTab() = runBlocking {
        val tab = createTab("https://example.org/normal", title = "Normal page")
        val browserStore = tabsTestStore(BrowserState(tabs = listOf(tab), selectedTabId = tab.id))
        val useCases = TabsUseCases(browserStore)
        val closedTabs = RecentlyClosedTabs(useCases)

        closedTabs.remember(listOf(tab))
        useCases.removeTab(tab.id)
        assertTrue(browserStore.state.tabs.isEmpty())

        assertTrue(closedTabs.reopen())
        val reopened = browserStore.state.tabs.single()
        assertEquals(tab.content.url, reopened.content.url)
        assertEquals(tab.content.title, reopened.content.title)
        assertFalse(reopened.content.private)
        assertNotEquals(tab.id, reopened.id)
        assertEquals(reopened.id, browserStore.state.selectedTabId)
        assertEquals(0, closedTabs.count.first())
        assertFalse(closedTabs.reopen())
    }

    @Test
    fun privateTabWithSameUrlCannotReplaceNormalTitle() = runBlocking {
        val url = "https://example.org/shared"
        history.remember(listOf(createTab(url, title = "Normal title")))
        history.remember(listOf(createTab(url, title = "Private title", private = true)))

        assertEquals(1, history.count.first())
        assertTrue(history.reopen())
        assertEquals("Normal title", store.state.tabs.single().content.title)
        assertFalse(store.state.tabs.single().content.private)
        assertFalse(history.reopen())
    }

    @Test
    fun privateBatchDoesNotEvictNormalHistoryAtCapacity() = runBlocking {
        val normalTabs = (1..10).map { createTab("https://example.org/normal/$it") }
        history.remember(normalTabs)
        history.remember(
            (1..15).map { createTab("https://example.org/private/$it", private = true) },
        )

        assertEquals(10, history.count.first())
        repeat(10) { assertTrue(history.reopen()) }
        assertEquals(normalTabs.map { it.content.url }, store.state.tabs.map { it.content.url })
        assertTrue(store.state.tabs.none { it.content.private })
        assertFalse(history.reopen())
    }

    @Test
    fun normalHistoryRemainsLimitedToTenMostRecentEntries() = runBlocking {
        (1..12).forEach {
            history.remember(listOf(createTab("https://example.org/normal/$it")))
        }

        assertEquals(10, history.count.first())
        repeat(10) { assertTrue(history.reopen()) }
        assertEquals(
            (12 downTo 3).map { "https://example.org/normal/$it" },
            store.state.tabs.map { it.content.url },
        )
        assertFalse(history.reopen())
    }

    @Test
    fun repeatedNormalUrlUsesLatestTitleOnce() = runBlocking {
        val url = "https://example.org/normal"
        history.remember(listOf(createTab(url, title = "Old title")))
        history.remember(listOf(createTab(url, title = "New title")))

        assertEquals(1, history.count.first())
        assertTrue(history.reopen())
        assertEquals("New title", store.state.tabs.single().content.title)
        assertFalse(history.reopen())
    }

    @Test
    fun ignoresBlankUrlsAndEmptyBatches() = runBlocking {
        history.remember(listOf(createTab(""), createTab("  ")))
        history.remember(emptyList())

        assertEquals(0, history.count.first())
        assertFalse(history.reopen())
        assertTrue(store.state.tabs.isEmpty())
    }
}
