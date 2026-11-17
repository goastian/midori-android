package org.midorinext.android.newtab

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.CustomTabListAction
import mozilla.components.browser.state.action.LastAccessAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.action.UndoAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

@Singleton
class HomeContinuationMiddleware @Inject constructor() : Middleware<BrowserState, BrowserAction> {
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()

    override fun invoke(store: Store<BrowserState, BrowserAction>, next: (BrowserAction) -> Unit, action: BrowserAction) {
        next(action)
        if (revision.subscriptionCount.value == 0) return
        when (action) {
            is TabListAction,
            is CustomTabListAction.TurnCustomTabIntoNormalTabAction,
            is UndoAction.RestoreRecoverableTabs,
            is LastAccessAction.UpdateLastAccessAction,
            is ContentAction.UpdateUrlAction,
            is ContentAction.UpdateTitleAction -> revision.update { it + 1 }
            else -> Unit
        }
    }
}
