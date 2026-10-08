package org.midorinext.android.mozac

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.preferences.app.AppPreferences

class BrowserSessionLifecycleTest {
    @Test
    fun restoresExistingSessionWhenDisabled() = runBlocking {
        val store = BrowserStore()
        var restored = false
        val lifecycle = BrowserSessionLifecycle(
            preferences = flowOf(preferences(false)),
            store = store,
            clearSavedSession = { error("Saved tabs should be kept") },
            restoreSavedSession = { restored = true },
        )

        lifecycle.restore()

        assertTrue(restored)
    }

    @Test
    fun discardsSavedSessionWhenEnabledAndPreservesIncomingLink() = runBlocking {
        val incomingTab = createTab("https://example.org/incoming")
        val store = BrowserStore(BrowserState(tabs = listOf(incomingTab)))
        var cleared = false
        val lifecycle = BrowserSessionLifecycle(
            preferences = flowOf(preferences(true)),
            store = store,
            clearSavedSession = { cleared = true },
            restoreSavedSession = { error("Previous tabs should not be restored") },
        )

        lifecycle.restore()

        assertTrue(cleared)
        assertTrue(store.state.restoreComplete)
        assertEquals(listOf(incomingTab), store.state.tabs)
    }

    @Test
    fun closesNormalAndPrivateTabsBeforeClearingSavedSession() = runBlocking {
        val tabs = listOf(
            createTab("https://example.org"),
            createTab("https://example.org/private", private = true),
        )
        var recoverable: Boolean? = null
        val store = BrowserStore(
            initialState = BrowserState(tabs = tabs, selectedTabId = tabs.first().id),
            middleware = listOf({ _, next, action ->
                if (action is TabListAction.RemoveAllTabsAction) recoverable = action.recoverable
                next(action)
            }),
        )
        var cleared = false
        val lifecycle = BrowserSessionLifecycle(
            preferences = flowOf(preferences(true)),
            store = store,
            clearSavedSession = {
                assertTrue(store.state.tabs.isEmpty())
                cleared = true
            },
            restoreSavedSession = {},
        )

        lifecycle.close()

        assertTrue(cleared)
        assertEquals(false, recoverable)
        assertNull(store.state.selectedTabId)
    }

    @Test
    fun checksLatestPreferenceAtExit() = runBlocking {
        val tab = createTab("https://example.org")
        val store = BrowserStore(BrowserState(tabs = listOf(tab)))
        val preferenceFlow = MutableStateFlow(preferences(true))
        var cleared = false
        val lifecycle = BrowserSessionLifecycle(
            preferences = preferenceFlow,
            store = store,
            clearSavedSession = { cleared = true },
            restoreSavedSession = {},
        )
        preferenceFlow.value = preferences(false)

        lifecycle.close()

        assertFalse(cleared)
        assertEquals(listOf(tab), store.state.tabs)

        preferenceFlow.value = preferences(true)
        lifecycle.close()

        assertTrue(cleared)
        assertTrue(store.state.tabs.isEmpty())
    }

    private fun preferences(enabled: Boolean): AppPreferences = AppPreferences.newBuilder()
        .setCloseTabsOnExit(enabled)
        .build()
}
