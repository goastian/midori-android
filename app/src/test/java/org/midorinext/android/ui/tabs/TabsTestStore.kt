package org.midorinext.android.ui.tabs

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.Middleware

internal fun tabsTestStore(
    initialState: BrowserState = BrowserState(),
    middleware: List<Middleware<BrowserState, BrowserAction>> = emptyList(),
): BrowserStore = BrowserStore(
    initialState = initialState,
    middleware = middleware + listOf<Middleware<BrowserState, BrowserAction>>({ _, next, action ->
        // Tab state is real; loading pages requires Gecko, which is unavailable in JVM tests.
        if (action !is EngineAction.LoadUrlAction) next(action)
    }),
)
