/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.vip

import android.util.Log
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature to enable tracking protection extension
 */
@Singleton
class MidoriVIPFeature @Inject constructor(
    private val vipState: VIPState
) {
    private var shouldAskStatusAfterConnectionForUrl: String? = null

    private val portDelegate = object: WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            try {
                with(message as JSONObject) {
                    when (this.getString("action")) {
                        "tabStatus" -> {
                            vipState.enabled = this.getBoolean("protectionEnabled")
                            vipState.isLoading = false
                        }
                        "popupSnapshot" -> {
                            vipState.hostname = this.optString("hostname")
                            vipState.enabled = this.optBoolean("protectionEnabled", true)
                            vipState.whitelisted = this.optBoolean("whitelisted", false)
                            vipState.blockedCount = this.optInt("blocked", 0)

                            this.optJSONObject("groupCounts")?.let { groups ->
                                vipState.trackersCount = groups.optInt("trackers", 0)
                                vipState.adsCount = groups.optInt("ads", 0)
                                vipState.otherCount = groups.optInt("other", 0)
                            }

                            this.optJSONObject("blockedByCategory")?.let { blockedByCategory ->
                                vipState.popupBlockedCount = blockedByCategory.optInt("popups", 0)
                            }

                            vipState.dataSavedBytes = this.optLong("dataSaved", 0L)
                            vipState.energySavedWh = this.optDouble("energySaved", 0.0)
                            vipState.co2SavedGrams = this.optDouble("co2Saved", 0.0)
                            vipState.protectionLevel = this.optString("protectionLevel", "standard")

                            this.optJSONObject("antiFingerprint")?.let { antiFingerprint ->
                                vipState.antiFingerprintEnabled = antiFingerprint.optBoolean("enabled", false)
                                vipState.antiFingerprintMode = antiFingerprint.optString("mode", "off")
                            }

                            this.optJSONObject("popupDefense")?.let { popupDefense ->
                                vipState.popupDefenseEnabled = popupDefense.optBoolean("enabled", false)
                                vipState.popupDefenseMode = popupDefense.optString("mode", "relaxed")
                            }

                            vipState.hasSnapshot = true
                            vipState.isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MIDORI_VIP", "Could not parse message from webextension", e)
                vipState.isLoading = false
            }
        }

        override fun onDisconnect(port: WebExtension.Port) {
            if (port == vipState.communicationPort) {
                vipState.communicationPort = null
                vipState.isLoading = false
            }
        }
    }

    private val messageDelegate = object: WebExtension.MessageDelegate {
        override fun onConnect(port: WebExtension.Port) {
            Log.d("MIDORI_VIP", "Communication port between native and webextension opened")
            vipState.communicationPort = port
            vipState.communicationPort?.setDelegate(portDelegate)
            shouldAskStatusAfterConnectionForUrl?.let { askStatus(it) }
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

    fun restoreTabsDone(selectedTabUrl: String) {
        vipState.updateSelectedTab(selectedTabUrl)
        if (vipState.communicationPort != null) {
            askStatus(selectedTabUrl)
        } else {
            shouldAskStatusAfterConnectionForUrl = selectedTabUrl
        }
    }

    private fun askStatus(selectedTabUrl: String) {
        Log.d("MIDORI_VIP", "askTabStatus - selected tab url is " + selectedTabUrl)
        vipState.isLoading = true
        vipState.communicationPort?.postMessage(JSONObject().also {
            it.put("action", "askPopupSnapshotForUrl")
            it.put("selectedTabUrl", selectedTabUrl)
        })
    }

    companion object {
        private const val URL = "resource://android/assets/midori_vip/"
    }
}