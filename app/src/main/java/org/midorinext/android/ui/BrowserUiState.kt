package org.midorinext.android.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import org.midorinext.android.preferences.app.AppPreferences

data class BrowserUiState(val preferences: AppPreferences, val isPrivate: Boolean)

internal fun browserUiStates(
    preferences: Flow<AppPreferences?>,
    browserStates: Flow<BrowserState>,
): Flow<BrowserUiState?> = combine(preferences, browserStates, ::resolvedBrowserUiState)
    .distinctUntilChanged()

internal fun resolvedBrowserUiState(preferences: AppPreferences?, state: BrowserState): BrowserUiState? {
    if (preferences == null || (!state.restoreComplete && state.selectedTab == null)) return null
    return BrowserUiState(preferences, state.selectedTab?.content?.private == true)
}
