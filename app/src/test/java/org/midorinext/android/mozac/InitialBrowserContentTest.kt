package org.midorinext.android.mozac

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.newtab.MidoriNewTabFeature

class InitialBrowserContentTest {
    @Test
    fun doesNotStartMaintenanceUntilTheSelectedDocumentHasPainted() = runBlocking {
        val states = MutableStateFlow(state(painted = false))
        val ready = async(start = CoroutineStart.UNDISPATCHED) { awaitInitialBrowserContent(states) }
        yield()
        assertFalse(ready.isCompleted)

        states.value = state(painted = true)
        assertTrue(ready.await())
    }

    @Test
    fun paintWhileTheDocumentIsStillLoadingDoesNotStartMaintenance() = runBlocking {
        val states = MutableStateFlow(state(loading = true))
        val ready = async(start = CoroutineStart.UNDISPATCHED) { awaitInitialBrowserContent(states) }
        yield()
        assertFalse(ready.isCompleted)

        states.value = state(loading = false)
        assertTrue(ready.await())
    }

    @Test
    fun waitsForSessionRestoration() = runBlocking {
        val states = MutableStateFlow(state(restored = false))
        val ready = async(start = CoroutineStart.UNDISPATCHED) { awaitInitialBrowserContent(states) }
        yield()
        assertFalse(ready.isCompleted)

        states.value = state(restored = true)
        assertTrue(ready.await())
    }

    @Test
    fun newTabPlaceholderDoesNotCountAsLoadedContent() = runBlocking {
        val states = MutableStateFlow(state(url = MidoriNewTabFeature.LOADING_URL))
        val ready = async(start = CoroutineStart.UNDISPATCHED) { awaitInitialBrowserContent(states) }
        yield()
        assertFalse(ready.isCompleted)

        states.value = state(url = "moz-extension://runtime-id/index.html")
        assertTrue(ready.await())
    }

    @Test
    fun privateDocumentCanReleaseMaintenanceWithoutRecordingItsUrl() = runBlocking {
        assertTrue(awaitInitialBrowserContent(flowOf(state(private = true))))
    }

    @Test
    fun noDocumentOrFailedLoadCannotPostponeMaintenanceIndefinitely() = runBlocking {
        assertFalse(awaitInitialBrowserContent(MutableStateFlow(BrowserState()), timeoutMillis = 10))
        assertFalse(awaitInitialBrowserContent(MutableStateFlow(state(painted = false)), timeoutMillis = 10))
    }

    private fun state(
        url: String = "https://example.org",
        painted: Boolean = true,
        loading: Boolean = false,
        restored: Boolean = true,
        private: Boolean = false,
    ): BrowserState {
        val tab = createTab(url, private = private).let { tab ->
            tab.copy(content = tab.content.copy(firstContentfulPaint = painted, loading = loading))
        }
        return BrowserState(tabs = listOf(tab), selectedTabId = tab.id, restoreComplete = restored)
    }
}
