/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.about

import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.midorinext.android.R
import org.midorinext.android.utils.Settings

/**
 * Triggers the "secret" debug menu when logoView is tapped 5 times.
 */
class SecretDebugMenuTrigger(
    val composeView: AboutComposeView,
    private val settings: Settings
) : DefaultLifecycleObserver {

    private var secretDebugMenuClicks = 0
    private var lastDebugMenuToast: Toast? = null

    init {
        if (!settings.showSecretDebugMenuThisSession) {
            composeView.onLogoClick = ::onLogoClick
        }
    }

    /**
     * Reset the [secretDebugMenuClicks] counter.
     */
    override fun onResume(owner: LifecycleOwner) {
        secretDebugMenuClicks = 0
    }

    private fun onLogoClick() {
        // Because the user will mostly likely tap the logo in rapid succession,
        // we ensure only 1 toast is shown at any given time.
        lastDebugMenuToast?.cancel()
        secretDebugMenuClicks += 1
        when (secretDebugMenuClicks) {
            in 2 until SECRET_DEBUG_MENU_CLICKS -> {
                val clicksLeft = SECRET_DEBUG_MENU_CLICKS - secretDebugMenuClicks
                val toast = Toast.makeText(
                    composeView.context,
                    composeView.context.getString(R.string.about_debug_menu_toast_progress, clicksLeft),
                    Toast.LENGTH_SHORT
                )
                toast.show()
                lastDebugMenuToast = toast
            }
            SECRET_DEBUG_MENU_CLICKS -> {
                Toast.makeText(
                    composeView.context,
                    R.string.about_debug_menu_toast_done,
                    Toast.LENGTH_LONG
                ).show()
                settings.showSecretDebugMenuThisSession = true
            }
        }
    }

    companion object {
        // Number of clicks on the app logo to enable the "secret" debug menu.
        private const val SECRET_DEBUG_MENU_CLICKS = 5
    }
}
