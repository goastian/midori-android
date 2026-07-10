package org.midorinext.android.adblock

import android.util.Log
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriPrivacyFeature @Inject constructor(
    private val adBlockerState: AdBlockerState
) {
    fun install(runtime: GeckoRuntime) {
        runtime.webExtensionController.ensureBuiltIn(LOCATION, ID).accept({ extension ->
            extension?.let {
                runOnUiThread {
                    runtime.webExtensionController.setAllowedInPrivateBrowsing(it, true)
                }
            }
        }, { error ->
            Log.e(TAG, "Failed to install Midori Privacy", error)
        })
    }

    fun restoreTabsDone(selectedTabUrl: String) {
        adBlockerState.updateSelectedTab(selectedTabUrl)
    }

    companion object {
        private const val TAG = "MidoriPrivacy"
        private const val ID = "midori-privacy@astian.org"
        private const val LOCATION = "resource://android/assets/extensions/midori_privacy/"
    }
}
