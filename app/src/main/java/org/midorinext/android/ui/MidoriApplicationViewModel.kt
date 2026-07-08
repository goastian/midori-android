package org.midorinext.android.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.cookies.MidoriCookieState
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.Appearance
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.storage.history.HistoryRepository
import org.midorinext.android.ui.zap.ZapState
import org.midorinext.android.usecases.ClearDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
    appPreferencesRepository: AppPreferencesRepository,
    clearDataUseCase: ClearDataUseCase,
    val cookieState: MidoriCookieState,
) : ViewModel() {
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
    data class SnackbarAction(val label: String, val apply: () -> Unit)
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
        .map { it.toolbarPosition }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ToolbarPosition.UNRECOGNIZED
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

    val zapOnQuit = appPreferencesRepository.flow
        .map { it.clearDataOnQuit }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val zapState: ZapState = ZapState(clearDataUseCase, viewModelScope, cookieState)
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