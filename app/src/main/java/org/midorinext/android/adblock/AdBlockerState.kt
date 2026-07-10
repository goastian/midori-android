package org.midorinext.android.adblock

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdBlockerState @Inject constructor() {
    private var lastProtectedHost: String? = null

    var enabled by mutableStateOf(true)
        internal set

    var hostname by mutableStateOf("")
        internal set

    var protectedPageCount by mutableStateOf(0)
        internal set

    var protectionLevel by mutableStateOf("balanced")
        internal set

    var hasSnapshot by mutableStateOf(false)
        internal set

    fun updateSelectedTab(url: String?) {
        if (url.isNullOrBlank() || url == "about:blank") {
            clearSnapshot(keepEnabled = enabled)
            lastProtectedHost = null
            return
        }

        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        if (host.isNotBlank()) {
            hostname = host
            enabled = true
            hasSnapshot = true
            if (host != lastProtectedHost) {
                protectedPageCount++
                lastProtectedHost = host
            }
        }
    }

    internal fun clearSnapshot(keepEnabled: Boolean = true) {
        if (!keepEnabled) {
            enabled = true
        }
        hostname = ""
        protectionLevel = "balanced"
        hasSnapshot = false
    }
}
