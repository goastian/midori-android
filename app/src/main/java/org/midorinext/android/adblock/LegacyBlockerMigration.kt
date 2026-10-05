package org.midorinext.android.adblock

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyBlockerMigration @Inject constructor() {
    fun uninstall(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.list().accept({ extensions ->
            extensions.orEmpty()
                .filter { it.id in LEGACY_IDS }
                .forEach { extension ->
                    controller.uninstall(extension).accept(
                        { Log.i(TAG, "Removed legacy blocker ${extension.id}") },
                        { error -> Log.e(TAG, "Could not remove legacy blocker ${extension.id}", error) },
                    )
                }
        }, { error -> Log.e(TAG, "Could not inspect legacy blockers", error) })
    }

    companion object {
        private const val TAG = "LegacyBlockerMigration"
        internal val LEGACY_IDS = setOf(
            "midori-protection@astian.org",
            "midori-privacy@astian.org",
            "easy-adblocker@easybrowser.local",
            "midori-vip-android@astian.org",
            "qwant-vip-android@qwant.com",
        )
    }
}
