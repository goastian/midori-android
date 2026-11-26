package org.midorinext.android.storage.autofill

import android.os.Handler
import android.os.Looper
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

internal class CreditCardPromptMiddleware : Middleware<BrowserState, BrowserAction> {
    private val handler = Handler(Looper.getMainLooper())

    override fun invoke(store: Store<BrowserState, BrowserAction>, next: (BrowserAction) -> Unit, action: BrowserAction) {
        if (action is EngineAction.LinkEngineSessionAction && org.midorinext.android.mozac.Gecko161Features.supportsSecurityCode) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                GeckoCreditCardBridge.install(action.engineSession)
            } else {
                handler.post { GeckoCreditCardBridge.install(action.engineSession) }
            }
        }
        next(action)
    }
}
