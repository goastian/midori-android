/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.cookies

import android.util.Log
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature to follow Midori cookies, know if user is logged in, and keep them after zap
 */
@Singleton
class MidoriCookieFeature @Inject constructor(
    cookieState: MidoriCookieState
) {
    private val portDelegate = object: WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            try {
                cookieState.messageReceived(JSONObject(message as String))
            } catch (e: Exception) {
                Log.e("MIDORI_COOKIES", "Could not parse message from webextension", e)
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (port == cookieState.communicationPort) {
                cookieState.communicationPort = null
            }
        }
    }

    private val messageDelegate = object: WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            Log.d("MIDORI_COOKIES", "Communication port between native and webextension opened")
            cookieState.communicationPort = port
            cookieState.communicationPort?.setDelegate(portDelegate)
        }
    }

    /**
     * Installs the web extension in the runtime
     */
    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.installBuiltIn(URL).accept {
            it?.let { extension ->
                runOnUiThread {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                    extension.setMessageDelegate(messageDelegate, "midori_browser")
                }
            }
        }
    }

    companion object {
        private const val URL = "resource://android/assets/midori_cookies/"
    }
}
