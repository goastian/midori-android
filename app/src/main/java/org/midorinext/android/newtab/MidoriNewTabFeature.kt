package org.midorinext.android.newtab

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.midorinext.android.BuildConfig
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import org.json.JSONObject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Installs the official Midori Tab bundle as a built-in WebExtension and exposes its
 * runtime URL to the browser. The extension URL is assigned by Gecko, so callers must
 * wait for installation instead of persisting or guessing a moz-extension UUID.
 */
@Singleton
class MidoriNewTabFeature @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    sealed interface InstallState {
        data object Disabled : InstallState
        data object Installing : InstallState
        data class Ready(val pageUrl: String) : InstallState
        data object Failed : InstallState
    }

    val isEnabled: Boolean =
        BuildConfig.FLAVOR_version == "original" && BuildConfig.FLAVOR_target == "playstore"

    private val _state = MutableStateFlow<InstallState>(
        if (isEnabled) InstallState.Installing else InstallState.Disabled,
    )
    val state: StateFlow<InstallState> = _state.asStateFlow()

    private val _pageUrl = MutableStateFlow<String?>(null)
    val pageUrl: StateFlow<String?> = _pageUrl.asStateFlow()

    private var installRequested = false
    private val bundledVersion: String? by lazy {
        runCatching {
            context.assets.open(ASSET_MANIFEST_PATH)
                .bufferedReader()
                .use { reader -> JSONObject(reader.readText()).getString("version") }
        }.onFailure { error ->
            Log.e(TAG, "Could not read the bundled Midori New Tab version", error)
        }.getOrNull()
    }

    fun install(runtime: GeckoRuntime) {
        if (!isEnabled || state.value is InstallState.Ready || installRequested) return

        installRequested = true
        _state.value = InstallState.Installing
        val controller = runtime.webExtensionController
        controller.ensureBuiltIn(LOCATION, EXTENSION_ID).accept({ extension ->
            if (extension == null) {
                Log.e(TAG, "Midori New Tab installation returned no extension")
                runOnUiThread(::publishFailure)
            } else if (needsReinstall(extension.metaData.version, bundledVersion)) {
                Log.i(
                    TAG,
                    "Updating bundled Midori New Tab ${extension.metaData.version} -> $bundledVersion",
                )
                controller.installBuiltIn(LOCATION).accept(
                    ::publishInstalledExtension,
                    ::publishInstallError,
                )
            } else {
                publishInstalledExtension(extension)
            }
        }, ::publishInstallError)
    }

    fun currentPageUrl(): String? = pageUrl.value

    fun currentOrLoadingUrl(): String = currentPageUrl() ?: LOADING_URL

    fun isLoadingUrl(url: String?): Boolean = isEnabled && url == LOADING_URL

    fun isNewTabUrl(url: String?): Boolean {
        if (!isEnabled || url == null) return false
        if (isLoadingUrl(url)) return true
        return currentPageUrl()?.let(url::startsWith) == true
    }

    private fun publishInstalledExtension(extension: WebExtension?) {
        val baseUrl = extension?.metaData?.baseUrl
        if (baseUrl.isNullOrBlank()) {
            Log.e(TAG, "Midori New Tab installed without a base URL")
            runOnUiThread(::publishFailure)
            return
        }
        runOnUiThread { publishReadyUrl(baseUrl) }
    }

    private fun publishInstallError(error: Throwable?) {
        Log.e(TAG, "Failed to install Midori New Tab", error)
        runOnUiThread(::publishFailure)
    }

    private fun publishReadyUrl(baseUrl: String) {
        val readyUrl = pageUrl(baseUrl)
        _pageUrl.value = readyUrl
        _state.value = InstallState.Ready(readyUrl)
    }

    private fun publishFailure() {
        installRequested = false
        _pageUrl.value = null
        _state.value = InstallState.Failed
    }

    companion object {
        private const val TAG = "MidoriNewTab"
        const val EXTENSION_ID = "midoritabs@astian.org"
        const val LOCATION = "resource://android/assets/extensions/midori_newtab/"
        const val LOADING_URL = "about:blank#midori-newtab"
        private const val ASSET_MANIFEST_PATH = "extensions/midori_newtab/manifest.json"

        internal fun normalizedBaseUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/"

        internal fun pageUrl(baseUrl: String): String = "${normalizedBaseUrl(baseUrl)}index.html"

        internal fun needsReinstall(installedVersion: String?, bundledVersion: String?): Boolean =
            !bundledVersion.isNullOrBlank() && installedVersion != bundledVersion
    }
}
