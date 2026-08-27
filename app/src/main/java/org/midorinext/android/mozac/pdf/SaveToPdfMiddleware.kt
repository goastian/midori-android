package org.midorinext.android.mozac.pdf

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import javax.inject.Inject
import javax.inject.Singleton

/** Events emitted when Gecko cannot render the current page as a PDF. */
@Singleton
class PdfSaveEvents @Inject constructor() {
    private val _failures = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val failures = _failures.asSharedFlow()

    fun notifyFailure() {
        _failures.tryEmit(Unit)
    }
}

/**
 * Handles Gecko's PDF-export failure action before it reaches the store reducer.
 *
 * Android Components deliberately delegates this action to consumers. Consuming it here keeps a
 * failed export from interrupting the app and lets the browser surface a localized message.
 */
class SaveToPdfMiddleware(
    private val events: PdfSaveEvents,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is EngineAction.SaveToPdfExceptionAction -> {
                events.notifyFailure()
                return
            }

            // BrowserState's reducer intentionally requires consumers to handle both terminal
            // actions. A completed export already entered the download flow, so it needs no
            // additional state update here.
            is EngineAction.SaveToPdfCompleteAction -> return
            else -> next(action)
        }
    }
}
