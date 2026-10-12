package org.midorinext.android.ui.browser.mozaccompose.prompts

import mozilla.components.browser.state.selector.findTabOrCustomTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.prompt.PromptRequest
import java.util.concurrent.atomic.AtomicBoolean

internal class PromptResponse(
    private val store: BrowserStore,
    private val sessionId: String,
    private val request: PromptRequest,
) {
    private val completed = AtomicBoolean()

    fun respond(block: () -> Unit = {}) {
        val requiresSelectedTab = request is PromptRequest.SelectLoginPrompt || request is PromptRequest.SelectCreditCard ||
            request is PromptRequest.SelectAddress || request is PromptRequest.SaveLoginPrompt
        if (!requiresSelectedTab || store.state.selectedTabId == sessionId) complete(block)
    }

    fun dismiss() = complete { (request as? PromptRequest.Dismissible)?.onDismiss?.invoke() }

    private fun complete(block: () -> Unit) {
        val pending = store.state.findTabOrCustomTab(sessionId)?.content?.promptRequests
            ?.any { it.uid == request.uid } == true
        if (pending && completed.compareAndSet(false, true)) store.consumeRequest(sessionId, request, block)
    }
}
