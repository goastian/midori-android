package org.midorinext.android.ui.browser.mozaccompose.prompts

import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.PromptRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptResponseTest {
    @Test
    fun repeatedSelectionAndDismissalCompleteRequestOnlyOnce() {
        val fixture = Fixture()
        val response = PromptResponse(fixture.store, fixture.tabId, fixture.request)

        response.respond { fixture.confirmed++ }
        response.respond { fixture.confirmed++ }
        response.dismiss()

        assertEquals(1, fixture.confirmed)
        assertEquals(0, fixture.dismissed)
        assertTrue(fixture.store.state.tabs.single().content.promptRequests.isEmpty())
    }

    @Test
    fun removedRequestCannotBeConfirmedByLateSelection() {
        val fixture = Fixture()
        val response = PromptResponse(fixture.store, fixture.tabId, fixture.request)
        fixture.store.dispatch(ContentAction.ConsumePromptRequestAction(fixture.tabId, fixture.request))

        response.respond { fixture.confirmed++ }
        response.dismiss()

        assertEquals(0, fixture.confirmed)
        assertEquals(0, fixture.dismissed)
    }

    @Test
    fun switchingTabRejectsLateFillButAllowsDismissalOfOriginalRequest() {
        val fixture = Fixture()
        val other = createTab("https://other.org")
        fixture.store.dispatch(TabListAction.AddTabAction(other))
        val response = PromptResponse(fixture.store, fixture.tabId, fixture.request)
        fixture.store.dispatch(TabListAction.SelectTabAction(other.id))

        response.respond { fixture.confirmed++ }
        response.dismiss()

        assertEquals(0, fixture.confirmed)
        assertEquals(1, fixture.dismissed)
        assertTrue(fixture.store.state.tabs.first().content.promptRequests.isEmpty())
    }

    @Test
    fun callbackFailureStillConsumesRequest() {
        val fixture = Fixture()
        val response = PromptResponse(fixture.store, fixture.tabId, fixture.request)

        runCatching { response.respond { error("Engine callback failed") } }

        assertTrue(fixture.store.state.tabs.single().content.promptRequests.isEmpty())
    }

    @Test
    fun newerSelectionDiscardsEarlierSuggestions() {
        val fixture = Fixture()
        var secondDismissed = 0
        val newer = PromptRequest.SelectLoginPrompt(emptyList(), null, {}, { secondDismissed++ })
        fixture.store.dispatch(ContentAction.UpdatePromptRequestAction(fixture.tabId, newer))

        dismissObsoleteAutofillRequests(fixture.store, fixture.tabId)

        assertEquals(1, fixture.dismissed)
        assertEquals(0, secondDismissed)
        assertEquals(listOf(newer), fixture.store.state.tabs.single().content.promptRequests)
    }

    @Test
    fun suggestionsCannotReplacePendingPasswordSave() {
        val fixture = Fixture()
        val save = PromptRequest.SaveLoginPrompt(0, emptyList(), {}, {})
        fixture.store.dispatch(ContentAction.UpdatePromptRequestAction(fixture.tabId, save))
        var latestDismissed = 0
        val latest = PromptRequest.SelectLoginPrompt(emptyList(), null, {}, { latestDismissed++ })
        fixture.store.dispatch(ContentAction.UpdatePromptRequestAction(fixture.tabId, latest))

        dismissObsoleteAutofillRequests(fixture.store, fixture.tabId)

        assertEquals(1, fixture.dismissed)
        assertEquals(1, latestDismissed)
        assertEquals(listOf(save), fixture.store.state.tabs.single().content.promptRequests)
    }

    @Test
    fun loadingPageDoesNotDiscardLatestValidSuggestion() {
        val fixture = Fixture()
        fixture.store.dispatch(ContentAction.UpdateLoadingStateAction(fixture.tabId, true))

        dismissObsoleteAutofillRequests(fixture.store, fixture.tabId)

        assertEquals(0, fixture.dismissed)
        assertEquals(listOf(fixture.request), fixture.store.state.tabs.single().content.promptRequests)
    }

    private class Fixture {
        var confirmed = 0
        var dismissed = 0
        val request = PromptRequest.SelectLoginPrompt(emptyList(), null, {}, { dismissed++ })
        private val tab = createTab("https://example.org").let {
            it.copy(content = it.content.copy(promptRequests = listOf(request)))
        }
        val tabId = tab.id
        val store = BrowserStore(BrowserState(tabs = listOf(tab), selectedTabId = tab.id))
    }
}
