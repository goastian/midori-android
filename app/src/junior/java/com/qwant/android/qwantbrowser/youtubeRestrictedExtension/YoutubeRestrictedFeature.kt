package org.midorinext.android.youtubeRestrictedExtension

import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime

object YoutubeRestrictedFeature {
    private const val URL = "resource://android/assets/youtube_restricted_extension/"

    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.installBuiltIn(URL).accept {
            it?.let { extension ->
                runOnUiThread {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true)
                }
            }
        }
    }
}