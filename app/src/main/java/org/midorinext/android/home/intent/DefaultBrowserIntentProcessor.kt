/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.intent

import android.content.Intent
import androidx.navigation.NavController
import org.midorinext.android.HomeActivity
import org.midorinext.android.ext.openSetDefaultBrowserOption
import org.midorinext.android.ext.settings
import org.midorinext.android.onboarding.DefaultBrowserNotificationWorker.Companion.isDefaultBrowserNotificationIntent

/**
 * When the default browser notification is tapped we need to launch [openSetDefaultBrowserOption]
 *
 * This should only happens once in a user's lifetime since once the user taps on the default browser
 * notification, [settings.shouldShowDefaultBrowserNotification] will return false
 */
class DefaultBrowserIntentProcessor(
    private val activity: HomeActivity,
) : HomeIntentProcessor {

    override fun process(intent: Intent, navController: NavController, out: Intent): Boolean {
        return if (isDefaultBrowserNotificationIntent(intent)) {
            activity.openSetDefaultBrowserOption()
            true
        } else {
            false
        }
    }
}
