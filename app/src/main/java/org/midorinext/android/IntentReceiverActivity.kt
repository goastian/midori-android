/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.midorinext.android.ext.components
import org.midorinext.android.onboarding.WelcomeActivity

class IntentReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding has been completed
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val onboardingCompleted = prefs.getBoolean(
            getString(R.string.pref_key_onboarding_completed),
            false,
        )

        if (!onboardingCompleted) {
            val welcomeIntent = Intent(this, WelcomeActivity::class.java)
            welcomeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(welcomeIntent)
            finish()
            return
        }

        val intent = intent?.let { Intent(it) } ?: Intent()

        // Explicitly remove the new task and clear task flags (Our browser activity is a single
        // task activity and we never want to start a second task here).
        intent.flags = intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
        intent.flags = intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK.inv()

        // LauncherActivity is started with the "excludeFromRecents" flag (set in manifest). We
        // do not want to propagate this flag from the launcher activity to the browser.
        intent.flags = intent.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv()

        val utils = components.utils

        MainScope().launch {
            val processor = utils.intentProcessors.firstOrNull { it.process(intent) }

            val className = if (processor in utils.externalIntentProcessors) {
                ExternalAppBrowserActivity::class
            } else {
                BrowserActivity::class
            }

            intent.setClassName(applicationContext, className.java.name)

            startActivity(intent)
            finish()
        }
    }
}
