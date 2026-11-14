package org.midorinext.android.diagnostics

import io.sentry.Sentry
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.CrashAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

class DiagnosticsMiddleware : Middleware<BrowserState, BrowserAction> {
    override fun invoke(store: Store<BrowserState, BrowserAction>, next: (BrowserAction) -> Unit, action: BrowserAction) {
        next(action)
        if (action is CrashAction.SessionCrashedAction) {
            Sentry.captureException(GeckoContentProcessCrash())
        }
    }
}

internal class GeckoContentProcessCrash : RuntimeException()
