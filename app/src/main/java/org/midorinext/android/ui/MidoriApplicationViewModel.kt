package org.midorinext.android.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.preferences.app.AppPreferencesRepository
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

enum class PrivacyMode {
    NORMAL, PRIVATE, SELECTED_TAB_PRIVACY
}

// TODO Separate ApplicationViewModel into ThemeViewModel and ZapViewModel
//  but I don't where to put snackbar methods
@HiltViewModel
class MidoriApplicationViewModel @Inject constructor(
    store: BrowserStore,
    historyRepository: HistoryRepository,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val adBlockSettings: AdBlockSettings,
    clearDataUseCase: ClearDataUseCase,
) : ViewModel() {
    val adBlockConfiguration = adBlockSettings.state

    fun updateBlockingLevel(level: BlockingLevel) = adBlockSettings.setLevel(level)
    private val privacyMode = MutableStateFlow(PrivacyMode.SELECTED_TAB_PRIVACY)

    private val selectedTabPrivacy = store.flow()
        .map { state -> state.selectedTab?.content?.private ?: false }

    val hasHistory = historyRepository.hasHistoryFlow
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

    val toolbarPosition = appPreferencesRepository.flow
        .map { preferences ->
            if (preferences.toolbarPosition == ToolbarPosition.BOTTOM) ToolbarPosition.BOTTOM
            else ToolbarPosition.TOP
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ToolbarPosition.TOP
        )

    val isPrivate = privacyMode
        .combine(selectedTabPrivacy) { privacyMode, selectedTabPrivacy ->
            when (privacyMode) {
                PrivacyMode.NORMAL -> false
                PrivacyMode.PRIVATE -> true
                PrivacyMode.SELECTED_TAB_PRIVACY -> selectedTabPrivacy
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val appearance = appPreferencesRepository.flow
        .map { it.appearance }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = Appearance.UNRECOGNIZED
        )

    fun setPrivacyMode(mode: PrivacyMode) {
        privacyMode.update { mode }
    }

    fun updateToolbarPosition(position: ToolbarPosition) {
        viewModelScope.launch {
            appPreferencesRepository.updateToolbarPosition(position)
        }
    }

    val searchEngine = appPreferencesRepository.flow
        .map { preferences ->
            if (preferences.searchEngine == SearchEnginePreference.UNRECOGNIZED) {
                SearchEnginePreference.ASTIANGO
            } else {
                preferences.searchEngine
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SearchEnginePreference.ASTIANGO,
        )

    fun updateSearchEngine(engine: SearchEnginePreference) {
        viewModelScope.launch {
            appPreferencesRepository.updateSearchEngine(engine)
        }
    }

    val showQuitApp = appPreferencesRepository.flow
        .map { it.clearDataOnQuit || it.closeTabsOnExit }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
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

    val zapState: ZapState = ZapState(clearDataUseCase, viewModelScope)
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
