package org.midorinext.android.vpn

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

/** Installs MidoriVPN as a built-in Gecko extension, scoped to Midori's traffic. */
@Singleton
class MidoriVpnFeature @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var installRequested = false
    private val bundledVersion: String? by lazy {
        runCatching {
            context.assets.open(ASSET_MANIFEST_PATH)
                .bufferedReader()
                .use { reader -> JSONObject(reader.readText()).getString("version") }
        }.onFailure { error ->
            Log.e(TAG, "Could not read the bundled MidoriVPN version", error)
        }.getOrNull()
    }

    fun install(runtime: GeckoRuntime) {
        if (installRequested) return
        installRequested = true

        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ extension ->
            if (extension == null) {
                publishInstallError(IllegalStateException("MidoriVPN installation returned no extension"))
            } else if (needsReinstall(extension.metaData.version, bundledVersion)) {
                Log.i(TAG, "Updating bundled MidoriVPN ${extension.metaData.version} -> $bundledVersion")
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
            publishInstallError(IllegalStateException("MidoriVPN update returned no extension"))
            return
        }

        runOnUiThread {
            runtime.webExtensionController.setAllowedInPrivateBrowsing(extension, true).accept(
                { Log.i(TAG, "MidoriVPN ${extension.metaData.version} is ready") },
                { error -> Log.e(TAG, "Could not allow MidoriVPN in private browsing", error) },
            )
        }
    }

    private fun publishInstallError(error: Throwable?) {
        installRequested = false
        Log.e(TAG, "Failed to install MidoriVPN", error)
    }

    companion object {
        private const val TAG = "MidoriVPN"
        const val EXTENSION_ID = "midorivpn@astian.org"
        const val LOCATION = "resource://android/assets/extensions/midori_vpn/"
        private const val ASSET_MANIFEST_PATH = "extensions/midori_vpn/manifest.json"

        internal fun needsReinstall(installedVersion: String?, bundledVersion: String?): Boolean =
            !bundledVersion.isNullOrBlank() && installedVersion != bundledVersion
    }
}
