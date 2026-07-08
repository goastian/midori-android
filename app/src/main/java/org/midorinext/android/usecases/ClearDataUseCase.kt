package org.midorinext.android.usecases

import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.ClearDataPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.storage.HistoryStorage
import javax.inject.Inject

// TODO delegate clearDataPreference management elsewhere ?
class ClearDataUseCase @Inject constructor(
    private val appPrefs: AppPreferencesRepository,
    private val engine: Engine,
    private val store: BrowserStore,
    private val historyStorage: HistoryStorage
) {
    private val coroutineScope: CoroutineScope = MainScope()
    private var prefs : ClearDataPreferences? = null

    init {
        coroutineScope.launch {
            appPrefs.clearDataPreferencesFlow.collect {
                prefs = it
            }
        }
    }

    operator fun invoke(then: (success: Boolean) -> Unit = {}) {
        val p = prefs ?: run {
            then(false)
            return
        }
        val historyJob: Job? = if (p.history) {
            coroutineScope.launch {
                historyStorage.deleteEverything()
            }
        } else null

        if (p.tabs) {
            store.dispatch(TabListAction.RemoveAllTabsAction(recoverable = true))
        } else {
            // Always remove private tabs, no matter clearDataPreferences
            store.dispatch(TabListAction.RemoveAllPrivateTabsAction)
        }

        val onBrowsingDataComplete: (Boolean) -> Unit = { browsingDataSuccess ->
            coroutineScope.launch {
                historyJob?.join()
                then(browsingDataSuccess)
            }
        }

        if (p.browsingData.types != 0) {
            engine.clearData(
                p.browsingData,
                onSuccess = { onBrowsingDataComplete(true) },
                onError = { onBrowsingDataComplete(false) }
            )
        } else {
            onBrowsingDataComplete(true)
        }
    }
}