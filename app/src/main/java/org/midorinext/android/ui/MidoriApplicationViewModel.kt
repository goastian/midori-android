package org.midorinext.android.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.adblock.AdBlockSettings
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.storage.history.HistoryRepository
import org.midorinext.android.ui.zap.ZapState
import org.midorinext.android.usecases.ClearDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.ext.flow
import javax.inject.Inject

// TODO Separate ApplicationViewModel into ThemeViewModel and ZapViewModel
//  but I don't where to put snackbar methods
@HiltViewModel
class MidoriApplicationViewModel @Inject constructor(
    store: BrowserStore,
    historyRepository: dagger.Lazy<HistoryRepository>,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val adBlockSettings: AdBlockSettings,
    clearDataUseCase: dagger.Lazy<ClearDataUseCase>,
) : ViewModel() {
    val preferencesSnapshot = appPreferencesRepository.snapshot
    val browserUiState = browserUiStates(preferencesSnapshot, store.flow()).stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = resolvedBrowserUiState(preferencesSnapshot.value, store.state),
    )

    val adBlockConfiguration = adBlockSettings.state

    fun updateBlockingLevel(level: BlockingLevel) = adBlockSettings.setLevel(level)
    val hasHistory = flow { emitAll(historyRepository.get().hasHistoryFlow) }
        .flowOn(kotlinx.coroutines.Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val snackbarHostState = SnackbarHostState()
    private var tabClosureSnackbarJob: Job? = null

    data class SnackbarAction(val label: String, val apply: () -> Unit)

    /**
     * Tab closures are frequent and have no follow-up action. Keep only the latest one so a
     * rapid series of closes cannot leave stale snackbars queued after the user moves on.
     */
    fun showTabClosureSnackbar(message: String) {
        tabClosureSnackbarJob?.cancel()
        tabClosureSnackbarJob = viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
        }
    }

    fun showSnackbar(
        message: String,
        action: SnackbarAction? = null,
        withDismissAction: Boolean = true,
        duration: SnackbarDuration = SnackbarDuration.Long
    ) {
        viewModelScope.launch {
            when (snackbarHostState.showSnackbar(message, action?.label, withDismissAction, duration)) {
                SnackbarResult.ActionPerformed -> { action?.apply?.invoke() }
                SnackbarResult.Dismissed -> {}
            }
        }
    }

    val toolbarPosition = preferencesSnapshot.filterNotNull()
        .map { preferences ->
            if (preferences.toolbarPosition == ToolbarPosition.BOTTOM) ToolbarPosition.BOTTOM
            else ToolbarPosition.TOP
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = (preferencesSnapshot.value ?: AppPreferencesSerializer.defaultValue).toolbarPosition
                .let { if (it == ToolbarPosition.BOTTOM) it else ToolbarPosition.TOP }
        )

    val isPrivate = store.flow()
        .map { state -> state.selectedTab?.content?.private ?: false }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = store.state.selectedTab?.content?.private ?: false
        )

    val appearance = preferencesSnapshot.filterNotNull()
        .map { it.appearance }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesSnapshot.value?.appearance ?: Appearance.SYSTEM_SETTINGS
        )

    fun updateToolbarPosition(position: ToolbarPosition) {
        viewModelScope.launch {
            appPreferencesRepository.updateToolbarPosition(position)
        }
    }

    val searchEngine = preferencesSnapshot.filterNotNull()
        .map { preferences ->
            if (preferences.searchEngine == SearchEnginePreference.UNRECOGNIZED) {
                SearchEnginePreference.ASTIANGO
            } else {
                preferences.searchEngine
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = preferencesSnapshot.value?.searchEngine
                ?.takeUnless { it == SearchEnginePreference.UNRECOGNIZED } ?: SearchEnginePreference.ASTIANGO,
        )

    fun updateSearchEngine(engine: SearchEnginePreference) {
        viewModelScope.launch {
            appPreferencesRepository.updateSearchEngine(engine)
        }
    }

    val showQuitApp = preferencesSnapshot.filterNotNull()
        .map { it.clearDataOnQuit || it.closeTabsOnExit }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    fun quit(then: () -> Unit) {
        viewModelScope.launch {
            if (appPreferencesRepository.flow.first().clearDataOnQuit) {
                zap(skipConfirmation = true) { success -> if (success) then() }
            } else {
                then()
            }
        }
    }

    val zapState: ZapState = ZapState({ then -> clearDataUseCase.get()(then) }, viewModelScope)
    fun zap(
        from: String = "Toolbar",
        skipConfirmation: Boolean = false,
        then: (Boolean) -> Unit = {}
    ) {
        zapState.zap(skipConfirmation) { success ->
            then(success)
        }
    }
}
