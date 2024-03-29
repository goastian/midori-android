/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("DEPRECATION")

package org.midorinext.android.helpers

import androidx.test.espresso.IdlingRegistry
import androidx.test.rule.ActivityTestRule
import org.midorinext.android.HomeActivity
import org.midorinext.android.helpers.idlingresource.AddonsInstallingIdlingResource

object IdlingResourceHelper {

    // Idling Resource to manage installing an addon
    fun registerAddonInstallingIdlingResource(activityTestRule: ActivityTestRule<HomeActivity>) {
        IdlingRegistry.getInstance().register(
            AddonsInstallingIdlingResource(
                activityTestRule.activity.supportFragmentManager
            )
        )
    }

    fun unregisterAddonInstallingIdlingResource(activityTestRule: ActivityTestRule<HomeActivity>) {
        IdlingRegistry.getInstance().unregister(
            AddonsInstallingIdlingResource(
                activityTestRule.activity.supportFragmentManager
            )
        )
    }

    fun unregisterAllIdlingResources() {
        for (resource in IdlingRegistry.getInstance().resources) {
            IdlingRegistry.getInstance().unregister(resource)
        }
    }
}
