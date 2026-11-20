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

enum class PagePrintFailure {
    SAVE_TO_PDF,
    PRINT,
}

@Singleton
class PagePrintEvents @Inject constructor() {
    private val _failures = MutableSharedFlow<PagePrintFailure>(extraBufferCapacity = 1)

    val failures = _failures.asSharedFlow()

    fun notifyFailure(failure: PagePrintFailure) {
        _failures.tryEmit(failure)
    }
}

/** Consumes the print and PDF export results that Android Components delegates to the app. */
class PagePrintMiddleware(
    private val events: PagePrintEvents,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is EngineAction.SaveToPdfExceptionAction -> events.notifyFailure(PagePrintFailure.SAVE_TO_PDF)
            is EngineAction.PrintContentExceptionAction -> events.notifyFailure(PagePrintFailure.PRINT)
            is EngineAction.SaveToPdfCompleteAction,
            is EngineAction.PrintContentCompletedAction -> Unit
            else -> next(action)
        }
    }
}
