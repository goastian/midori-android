package org.midorinext.android.ui

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.ToolbarPosition

class BrowserUiStateTest {
    @Test(timeout = 5_000)
    fun firstReadyStateContainsPersistedPreferencesAndRestoredPrivateSelectionTogether() = runBlocking {
        val persisted = AppPreferencesSerializer.defaultValue.toBuilder()
            .setAppearance(Appearance.DARK)
            .setToolbarPosition(ToolbarPosition.BOTTOM)
            .build()
        val preferences = MutableStateFlow<AppPreferences?>(null)
        val browserStates = MutableStateFlow(BrowserState())
        val updates = Channel<BrowserUiState?>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            browserUiStates(preferences, browserStates).collect { updates.send(it) }
        }
        try {
            assertNull(updates.receive())
            preferences.value = persisted
            val privateTab = createTab("about:privatebrowsing", private = true)
            browserStates.value = BrowserState(
                tabs = listOf(privateTab),
                selectedTabId = privateTab.id,
                restoreComplete = true,
            )

            val firstReady = updates.receive()
            assertNotNull(firstReady)
            assertEquals(persisted, firstReady!!.preferences)
            assertTrue(firstReady.isPrivate)

            val updated = persisted.toBuilder().setAppearance(Appearance.LIGHT).build()
            preferences.value = updated
            assertEquals(BrowserUiState(updated, isPrivate = true), updates.receive())
        } finally {
            collector.cancel()
            updates.close()
        }
    }

    @Test(timeout = 5_000)
    fun restoredEmptySessionAllowsTheBrowserToCreateItsSafetyTab() = runBlocking {
        val preferences = AppPreferencesSerializer.defaultValue
        val state = browserUiStates(
            flowOf(preferences),
            flowOf(BrowserState(restoreComplete = true)),
        ).first()

        assertEquals(BrowserUiState(preferences, isPrivate = false), state)
    }

    @Test(timeout = 5_000)
    fun incomingSelectionCanBeShownBeforeSavedSessionRestorationCompletes() = runBlocking {
        val preferences = AppPreferencesSerializer.defaultValue
        val incomingTab = createTab("https://example.org/incoming")
        val state = browserUiStates(
            flowOf(preferences),
            flowOf(BrowserState(tabs = listOf(incomingTab), selectedTabId = incomingTab.id)),
        ).first()

        assertEquals(BrowserUiState(preferences, isPrivate = false), state)
    }
}
