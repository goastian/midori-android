package org.midorinext.android.adblock

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriPrivacyFeature @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var installRequested = false
    private val bundledVersion: String? by lazy {
        runCatching {
            context.assets.open(ASSET_MANIFEST_PATH)
                .bufferedReader()
                .use { reader -> JSONObject(reader.readText()).getString("version") }
        }.onFailure { error ->
            Log.e(TAG, "Could not read the bundled Midori Privacy version", error)
        }.getOrNull()
    }

    fun install(runtime: GeckoRuntime) {
        if (installRequested) return
        installRequested = true

        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ extension ->
            if (extension == null) {
                publishInstallError(IllegalStateException("Midori Privacy installation returned no extension"))
            } else if (needsReinstall(extension.metaData.version, bundledVersion)) {
                Log.i(
                    TAG,
                    "Updating bundled Midori Privacy ${extension.metaData.version} -> $bundledVersion",
                )
                controller.installBuiltIn(LOCATION).accept(
                    { installed -> publishInstalledExtension(runtime, installed) },
                    ::publishInstallError,
                )
            } else {
                publishInstalledExtension(runtime, extension)
            }
        }, ::publishInstallError)
    }

    private fun publishInstalledExtension(runtime: GeckoRuntime, extension: WebExtension?) {
        if (extension == null) {
            publishInstallError(IllegalStateException("Midori Privacy update returned no extension"))
            return
        }

        runOnUiThread {
            runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true).accept(
                { Log.i(TAG, "Midori Privacy ${extension.metaData.version} is ready") },
                { error -> Log.e(TAG, "Could not allow Midori Privacy in private browsing", error) },
            )
        }
        removeLegacyExtension(runtime)
    }

    private fun removeLegacyExtension(runtime: GeckoRuntime) {
        val controller = runtime.webExtensionController
        controller.list().accept({ extensions ->
            extensions.orEmpty()
                .filter { extension -> extension.id in LEGACY_EXTENSION_IDS }
                .forEach { legacyExtension ->
                    controller.uninstall(legacyExtension).accept(
                        { Log.i(TAG, "Removed legacy blocker ${legacyExtension.id}") },
                        { error -> Log.e(TAG, "Failed to remove legacy blocker ${legacyExtension.id}", error) },
                    )
                }
        }, { error ->
            Log.e(TAG, "Could not inspect legacy Midori Privacy installations", error)
        })
    }

    private fun publishInstallError(error: Throwable?) {
        installRequested = false
        Log.e(TAG, "Failed to install Midori Privacy", error)
    }

    companion object {
        private const val TAG = "MidoriPrivacy"
        const val EXTENSION_ID = "midori-protection@astian.org"
        const val LOCATION = "resource://android/assets/extensions/midori_privacy/"
        internal const val LEGACY_EXTENSION_ID = "midori-privacy@astian.org"
        private const val ASSET_MANIFEST_PATH = "extensions/midori_privacy/manifest.json"
        internal val LEGACY_EXTENSION_IDS = setOf(
            LEGACY_EXTENSION_ID,
            "easy-adblocker@easybrowser.local",
            "midori-vip-android@astian.org",
            "qwant-vip-android@qwant.com",
        )

        internal fun needsReinstall(installedVersion: String?, bundledVersion: String?): Boolean =
            !bundledVersion.isNullOrBlank() && installedVersion != bundledVersion
    }
}
