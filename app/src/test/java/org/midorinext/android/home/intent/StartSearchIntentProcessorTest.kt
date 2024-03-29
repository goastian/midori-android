/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.intent

import android.content.Intent
import androidx.navigation.NavController
import androidx.navigation.navOptions
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.HomeActivity
import org.midorinext.android.NavGraphDirections
import org.midorinext.android.R
import org.midorinext.android.ext.nav
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.search.SearchEventSource

@RunWith(MidoriRobolectricTestRunner::class)
class StartSearchIntentProcessorTest {

    private val navController: NavController = mockk(relaxed = true)
    private val out: Intent = mockk(relaxed = true)

    @Test
    fun `do not process blank intents`() {
        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `do not process when search extra is false`() {
        val intent = Intent().apply {
            removeExtra(HomeActivity.OPEN_TO_SEARCH)
        }
        StartSearchIntentProcessor().process(intent, navController, out)

        verify { navController wasNot Called }
        verify { out wasNot Called }
    }

    @Test
    fun `process search intents`() {
        val intent = Intent().apply {
            putExtra(HomeActivity.OPEN_TO_SEARCH, StartSearchIntentProcessor.SEARCH_WIDGET)
        }
        StartSearchIntentProcessor().process(intent, navController, out)
        val options = navOptions {
            popUpTo = R.id.homeFragment
        }

        verify {
            navController.nav(
                null,
                NavGraphDirections.actionGlobalSearchDialog(
                    sessionId = null,
                    searchAccessPoint = SearchEventSource.WIDGET
                ),
                options
            )
        }
        verify { out.removeExtra(HomeActivity.OPEN_TO_SEARCH) }
    }
}
