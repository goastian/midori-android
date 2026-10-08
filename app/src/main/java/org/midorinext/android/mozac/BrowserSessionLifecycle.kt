package org.midorinext.android.mozac

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import mozilla.components.browser.state.action.RestoreCompleteAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore
import org.midorinext.android.preferences.app.AppPreferences

class BrowserSessionLifecycle(
    private val preferences: Flow<AppPreferences>,
    private val store: BrowserStore,
    private val clearSavedSession: suspend () -> Unit,
    private val restoreSavedSession: suspend () -> Unit,
) {
    suspend fun restore() {
        if (preferences.first().closeTabsOnExit) {
            clearSavedSession()
            store.dispatch(RestoreCompleteAction)
        } else {
            restoreSavedSession()
        }
    }

    suspend fun close() {
        if (preferences.first().closeTabsOnExit) {
            store.dispatch(TabListAction.RemoveAllTabsAction(recoverable = false))
            clearSavedSession()
        }
    }
}
