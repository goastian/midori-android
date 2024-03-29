/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.intent

import android.content.Intent
import androidx.navigation.NavController
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.search.ext.waitForSelectedOrDefaultSearchEngine
import org.midorinext.android.BrowserDirection
import org.midorinext.android.HomeActivity
import org.midorinext.android.widget.VoiceSearchActivity.Companion.SPEECH_PROCESSING

/**
 * The search widget has a microphone button to let users search with their voice.
 * Once the search is complete then a new search should be started.
 */
class SpeechProcessingIntentProcessor(
    private val activity: HomeActivity,
    private val store: BrowserStore,
) : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        if (
            !intent.hasExtra(SPEECH_PROCESSING) ||
            intent.extras?.getBoolean(HomeActivity.OPEN_TO_BROWSER_AND_LOAD) != true
        ) {
            return false
        }

        out.putExtra(HomeActivity.OPEN_TO_BROWSER_AND_LOAD, false)

        store.waitForSelectedOrDefaultSearchEngine { searchEngine ->
            if (searchEngine != null) {
                launchToBrowser(
                    searchEngine,
                    intent.getStringExtra(SPEECH_PROCESSING).orEmpty()
                )
            }
        }

        return true
    }

    private fun launchToBrowser(searchEngine: SearchEngine, text: String) {
        activity.openToBrowserAndLoad(
            searchTermOrURL = text,
            newTab = true,
            from = BrowserDirection.FromGlobal,
            engine = searchEngine,
            forceSearch = true
        )
    }
}
