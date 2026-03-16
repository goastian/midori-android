/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.tor

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import mozilla.components.ExperimentalAndroidComponentsApi
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.preferences.Branch
import org.midorinext.android.R
import org.midorinext.android.ext.components

/**
 * Bridges the Tor SOCKS5 proxy with GeckoView network settings.
 *
 * Uses Engine.setBrowserPref() to configure Gecko prefs at runtime:
 *   network.proxy.type = 1  → manual proxy
 *   network.proxy.socks = "127.0.0.1"
 *   network.proxy.socks_port = <port>
 *   network.proxy.socks_remote_dns = true  → DNS through Tor (prevents leaks)
 *   network.proxy.no_proxies_on = ""       → proxy everything
 *
 * When Tor is off:
 *   network.proxy.type = 5  → system proxy (default)
 */
@OptIn(ExperimentalAndroidComponentsApi::class)
object TorIntegration {

    private const val TAG = "TorIntegration"
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isTorActive: Boolean = false
        private set

    @Volatile
    var currentSocksPort: Int = -1
        private set

    /**
     * Called when Tor has bootstrapped and is ready.
     */
    fun onTorConnected(context: Context, socksPort: Int) {
        Log.i(TAG, "Tor connected on port $socksPort, configuring SOCKS proxy")
        currentSocksPort = socksPort
        isTorActive = true

        mainHandler.post {
            val engine = context.components.core.engine
            setBrowserPref(engine, "network.proxy.type", 1)
            setBrowserPref(engine, "network.proxy.socks", "127.0.0.1")
            setBrowserPref(engine, "network.proxy.socks_port", socksPort)
            setBrowserPref(engine, "network.proxy.socks_remote_dns", true)
            setBrowserPref(engine, "network.proxy.socks_version", 5)
            setBrowserPref(engine, "network.proxy.no_proxies_on", "")
            // Disable WebRTC to prevent IP leaks
            setBrowserPref(engine, "media.peerconnection.enabled", false)
            Log.i(TAG, "GeckoView proxy configured for Tor")
        }
    }

    /**
     * Called when Tor is stopped or disconnected.
     */
    fun onTorDisconnected(context: Context) {
        Log.i(TAG, "Tor disconnected, reverting to direct connection")
        isTorActive = false
        currentSocksPort = -1

        mainHandler.post {
            val engine = context.components.core.engine
            setBrowserPref(engine, "network.proxy.type", 5) // system default
            setBrowserPref(engine, "network.proxy.socks", "")
            setBrowserPref(engine, "network.proxy.socks_port", 0)
            setBrowserPref(engine, "network.proxy.socks_remote_dns", false)
            // Re-enable WebRTC
            setBrowserPref(engine, "media.peerconnection.enabled", true)
            Log.i(TAG, "GeckoView proxy reverted to direct")
        }
    }

    /**
     * Toggle Tor on/off. Returns true if Tor is being started.
     */
    fun toggle(context: Context): Boolean {
        return if (isTorActive) {
            TorForegroundService.stopTor(context)
            false
        } else {
            TorForegroundService.startTor(context)
            true
        }
    }

    /**
     * Check if user has enabled Tor for private browsing in preferences.
     */
    fun isTorEnabledInPrefs(context: Context): Boolean {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.pref_key_tor_enabled), false)
    }

    private fun setBrowserPref(engine: Engine, key: String, value: Int) {
        engine.setBrowserPref(
            key, value, Branch.USER,
            onSuccess = { Log.d(TAG, "Set $key=$value") },
            onError = { Log.e(TAG, "Failed to set $key", it) },
        )
    }

    private fun setBrowserPref(engine: Engine, key: String, value: String) {
        engine.setBrowserPref(
            key, value, Branch.USER,
            onSuccess = { Log.d(TAG, "Set $key=$value") },
            onError = { Log.e(TAG, "Failed to set $key", it) },
        )
    }

    private fun setBrowserPref(engine: Engine, key: String, value: Boolean) {
        engine.setBrowserPref(
            key, value, Branch.USER,
            onSuccess = { Log.d(TAG, "Set $key=$value") },
            onError = { Log.e(TAG, "Failed to set $key", it) },
        )
    }
}
