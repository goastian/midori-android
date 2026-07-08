package org.midorinext.android.vip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VIPState @Inject constructor() {
    internal var communicationPort: WebExtension.Port? = null
    private var selectedTabUrl: String? = null

    var enabled by mutableStateOf(true)
        internal set

    var hostname by mutableStateOf("")
        internal set

    var blockedCount by mutableStateOf(0)
        internal set

    var trackersCount by mutableStateOf(0)
        internal set

    var adsCount by mutableStateOf(0)
        internal set

    var otherCount by mutableStateOf(0)
        internal set

    var popupBlockedCount by mutableStateOf(0)
        internal set

    var dataSavedBytes by mutableStateOf(0L)
        internal set

    var energySavedWh by mutableStateOf(0.0)
        internal set

    var co2SavedGrams by mutableStateOf(0.0)
        internal set

    var protectionLevel by mutableStateOf("standard")
        internal set

    var antiFingerprintEnabled by mutableStateOf(false)
        internal set

    var antiFingerprintMode by mutableStateOf("off")
        internal set

    var popupDefenseEnabled by mutableStateOf(false)
        internal set

    var popupDefenseMode by mutableStateOf("relaxed")
        internal set

    var whitelisted by mutableStateOf(false)
        internal set

    var isLoading by mutableStateOf(false)
        internal set

    var hasSnapshot by mutableStateOf(false)
        internal set

    fun updateSelectedTab(url: String?) {
        selectedTabUrl = url
        if (url.isNullOrBlank() || url == "about:blank") {
            clearSnapshot(keepEnabled = enabled)
        }
    }

    fun requestPopupSnapshot() {
        requestPopupSnapshot(selectedTabUrl)
    }

    fun requestPopupSnapshot(url: String?) {
        if (!url.isNullOrBlank()) {
            selectedTabUrl = url
        }

        val port = communicationPort ?: run {
            isLoading = false
            return
        }

        isLoading = true
        port.postMessage(JSONObject().also {
            val selectedUrl = selectedTabUrl
            if (selectedUrl.isNullOrBlank()) {
                it.put("action", "askPopupSnapshot")
            } else {
                it.put("action", "askPopupSnapshotForUrl")
                it.put("selectedTabUrl", selectedUrl)
            }
        })
    }

    fun toggleProtection() {
        val port = communicationPort ?: run {
            isLoading = false
            return
        }

        isLoading = true
        port.postMessage(JSONObject().also {
            it.put("action", "toggleProtection")
        })
    }

    internal fun clearSnapshot(keepEnabled: Boolean = true) {
        if (!keepEnabled) {
            enabled = true
        }
        hostname = ""
        blockedCount = 0
        trackersCount = 0
        adsCount = 0
        otherCount = 0
        popupBlockedCount = 0
        dataSavedBytes = 0L
        energySavedWh = 0.0
        co2SavedGrams = 0.0
        protectionLevel = "standard"
        antiFingerprintEnabled = false
        antiFingerprintMode = "off"
        popupDefenseEnabled = false
        popupDefenseMode = "relaxed"
        whitelisted = false
        isLoading = false
        hasSnapshot = false
    }
}