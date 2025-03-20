/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.library.history.awesomebar

import mozilla.components.concept.engine.EngineSession
import mozilla.components.feature.awesomebar.provider.CombinedHistorySuggestionProvider
import mozilla.components.feature.session.SessionUseCases
import org.midorinext.android.HomeActivity
import org.midorinext.android.browser.browsingmode.BrowsingMode
import org.midorinext.android.components.Core.Companion.METADATA_SHORTCUT_SUGGESTION_LIMIT
import org.midorinext.android.ext.components
import org.midorinext.android.library.history.HistorySearchFragmentState

/**
 * View that contains and configures the BrowserAwesomeBar
 */
class AwesomeBarView(
    activity: HomeActivity,
    val interactor: AwesomeBarInteractor,
    val view: AwesomeBarWrapper,
) {
    private val combinedHistoryProvider: CombinedHistorySuggestionProvider

    private val loadUrlUseCase = object : SessionUseCases.LoadUrlUseCase {
        override operator fun invoke(
            url: String,
            flags: EngineSession.LoadUrlFlags,
            additionalHeaders: Map<String, String>?,
            originalInput: String?,
        ) {
            interactor.onUrlTapped(url, flags)
        }
    }

    init {
        val components = activity.components

        val engineForSpeculativeConnects = when (activity.browsingModeManager.mode) {
            BrowsingMode.Normal -> components.core.engine
            BrowsingMode.Private -> null
        }

        combinedHistoryProvider =
            CombinedHistorySuggestionProvider(
                historyStorage = components.core.historyStorage,
                historyMetadataStorage = components.core.historyStorage,
                loadUrlUseCase = loadUrlUseCase,
                icons = components.core.icons,
                engine = engineForSpeculativeConnects,
                maxNumberOfSuggestions = METADATA_SHORTCUT_SUGGESTION_LIMIT,
                showEditSuggestion = false,
            )

        view.addProviders(combinedHistoryProvider)
    }

    fun update(state: HistorySearchFragmentState) {
        view.onInputChanged(state.query)
    }
}
