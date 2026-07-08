package org.midorinext.android.extensions

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import mozilla.components.feature.addons.Addon
import mozilla.components.feature.addons.AddonManager
import org.midorinext.android.extensions.store.ExtensionRepository
import org.midorinext.android.extensions.store.InstalledExtensionProto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "ExtensionManager"

/** IDs of built-in extensions that should NOT appear in the user-facing list. */
private val BUILTIN_IDS = setOf(
    "midori-vip-android@astian.org",
    "midori-protection@astian.org",
    "qwant-vip-android@qwant.com",
    "midori-cookies-android@astian.org",
    "youtube-restricted@astian.org",
    "webcompat@mozilla.org"
)

/**
 * Central façade for managing browser extensions.
 *
 * It delegates actual WebExtension operations to Mozilla's [AddonManager]
 * and persists lightweight metadata in the protobuf-backed [ExtensionRepository]
 * so the UI always has a reactive [extensions] flow.
 */
@Singleton
class ExtensionManager @Inject constructor(
    private val addonManager: AddonManager,
    private val repository: ExtensionRepository
) {

    // ── Reactive list exposed to the UI ─────────────────────────────────

    /** Observable list of installed extensions, mapped to the UI model. */
    val extensions: Flow<List<ExtensionItem>> = repository.extensionsFlow.map { protos ->
        protos.map { it.toItem() }
    }

    // ── Public operations ───────────────────────────────────────────────

    /**
     * Install an extension from a URL (typically a `.xpi` file).
     *
     * Uses [AddonManager.installAddon] with callbacks, wrapped into a
     * suspend function. On success the extension is persisted to the local
     * DataStore.
     */
    suspend fun install(url: String) = withContext(Dispatchers.Main) {
        val addon = suspendCancellableCoroutine<Addon> { cont ->
            val operation = addonManager.installAddon(
                url = url,
                onSuccess = { installed -> cont.resume(installed) },
                onError = { throwable -> cont.resumeWithException(throwable) }
            )
            cont.invokeOnCancellation { operation.cancel() }
        }
        withContext(Dispatchers.IO) {
            repository.addExtension(addon.toProto(url))
        }
        Log.d(TAG, "Installed extension: ${addon.id}")
    }

    /** Uninstall an extension by its ID. */
    suspend fun uninstall(extensionId: String) = withContext(Dispatchers.Main) {
        val addon = resolveAddon(extensionId)
        suspendCancellableCoroutine { cont ->
            addonManager.uninstallAddon(
                addon,
                onSuccess = { cont.resume(Unit) },
                onError = { _, throwable -> cont.resumeWithException(throwable) }
            )
        }
        withContext(Dispatchers.IO) {
            repository.removeExtension(extensionId)
        }
        Log.d(TAG, "Uninstalled extension: $extensionId")
    }

    /** Enable or disable an extension by its ID. */
    suspend fun setEnabled(extensionId: String, enabled: Boolean) = withContext(Dispatchers.Main) {
        val addon = resolveAddon(extensionId)
        suspendCancellableCoroutine { cont ->
            if (enabled) {
                addonManager.enableAddon(
                    addon,
                    onSuccess = { cont.resume(Unit) },
                    onError = { throwable -> cont.resumeWithException(throwable) }
                )
            } else {
                addonManager.disableAddon(
                    addon,
                    onSuccess = { cont.resume(Unit) },
                    onError = { throwable -> cont.resumeWithException(throwable) }
                )
            }
        }
        withContext(Dispatchers.IO) {
            repository.setEnabled(extensionId, enabled)
        }
        Log.d(TAG, "Extension $extensionId enabled=$enabled")
    }

    /**
     * Fetch the list of available (recommended) add-ons from AMO,
     * merged with their installed state from GeckoView.
     * The [Addon] model already contains [Addon.installedState] when applicable.
     */
    suspend fun getAddons(allowCache: Boolean = true): List<Addon> = withContext(Dispatchers.IO) {
        try {
            addonManager.getAddons(allowCache = allowCache)
                .filter { it.id !in BUILTIN_IDS }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching add-ons", e)
            emptyList()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** Resolve an [Addon] by its ID from the current engine state. */
    private suspend fun resolveAddon(extensionId: String): Addon {
        return addonManager.getAddons(allowCache = true)
            .firstOrNull { it.id == extensionId }
            ?: throw IllegalStateException("Extension $extensionId not found")
    }
}

// ── Mapping helpers ─────────────────────────────────────────────────────

private fun InstalledExtensionProto.toItem() = ExtensionItem(
    id = id,
    name = name,
    description = description,
    version = version,
    iconUrl = iconUrl,
    enabled = enabled
)

private fun Addon.toProto(downloadUrl: String) = InstalledExtensionProto.newBuilder()
    .setId(id)
    .setName(translatableName.values.firstOrNull() ?: id)
    .setDescription(translatableDescription.values.firstOrNull().orEmpty())
    .setVersion(version)
    .setIconUrl(iconUrl.orEmpty())
    .setDownloadUrl(downloadUrl)
    .setEnabled(true)
    .setInstalledAt(System.currentTimeMillis())
    .build()


