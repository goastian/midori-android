/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.intent

import android.content.Intent
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SearchState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.search.ext.createSearchEngine
import mozilla.components.support.test.robolectric.testContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.BrowserDirection
import org.midorinext.android.HomeActivity
import org.midorinext.android.ext.components
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.helpers.perf.TestStrictModeManager
import org.midorinext.android.widget.VoiceSearchActivity.Companion.SPEECH_PROCESSING

@RunWith(MidoriRobolectricTestRunner::class)
class SpeechProcessingIntentProcessorTest {

    private val activity: HomeActivity = mockk(relaxed = true)
    private val navController: NavController = mockk(relaxed = true)
    private val out: Intent = mockk(relaxed = true)

    private val searchEngine = createSearchEngine(
        name = "Test",
        url = "https://www.example.org/?q={searchTerms}",
        icon = mockk()
    )

    private lateinit var store: BrowserStore

    @Before
    fun setup() {
        val searchEngine = searchEngine

        store = BrowserStore(
            BrowserState(
                search = SearchState(
                    customSearchEngines = listOf(searchEngine),
                    userSelectedSearchEngineId = searchEngine.id,
                    complete = true
                )
            )
        )

        every { activity.applicationContext } returns ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `do not process blank intents`() {
        val processor = SpeechProcessingIntentProcessor(activity, store)
        processor.process(Intent(), navController, out)

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `do not process when open extra is false`() {
        val intent = Intent().apply {
            putExtra(HomeActivity.OPEN_TO_BROWSER_AND_LOAD, false)
        }
        val processor = SpeechProcessingIntentProcessor(activity, store)
        processor.process(intent, navController, out)

        verify { activity wasNot Called }
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `reads the speech processing extra`() {
        every { testContext.components.strictMode } returns TestStrictModeManager()
        val intent = Intent().apply {
            putExtra(HomeActivity.OPEN_TO_BROWSER_AND_LOAD, true)
            putExtra(SPEECH_PROCESSING, "hello world")
        }

        val processor = SpeechProcessingIntentProcessor(activity, store)
        processor.process(intent, mockk(), mockk(relaxed = true))

        verify {
            activity.openToBrowserAndLoad(
                searchTermOrURL = "hello world",
                newTab = true,
                from = BrowserDirection.FromGlobal,
                forceSearch = true,
                engine = searchEngine
            )
        }
    }
}
