package org.midorinext.android.pwa

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.Icon
import mozilla.components.browser.icons.IconRequest
import mozilla.components.browser.state.state.SessionState
import mozilla.components.concept.engine.manifest.WebAppManifest
import mozilla.components.feature.pwa.ManifestStorage
import org.midorinext.android.R
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val icons: BrowserIcons,
) {
    private val database by lazy {
        Room.databaseBuilder(context, InstalledWebAppDatabase::class.java, "installed_web_apps.db").build()
    }
    private val dao get() = database.apps()
    private val manifestStorage by lazy { ManifestStorage(context) }

    val installedApps by lazy { dao.observeAll() }

    suspend fun get(startUrl: String): InstalledWebApp? = dao.get(startUrl)

    suspend fun manifest(startUrl: String): WebAppManifest? = manifestStorage.loadManifest(startUrl)

    suspend fun install(session: SessionState, manifest: WebAppManifest) {
        install(manifest, session.content.icon)
    }

    suspend fun installSite(url: String, name: String, fallbackIcon: Bitmap? = null) {
        val address = URI(url)
        require(address.scheme.equals("https", ignoreCase = true) && !address.host.isNullOrBlank())
        val origin = origin(url)
        install(
            WebAppManifest(
                name = name.ifBlank { URI(url).host ?: url },
                startUrl = url,
                display = WebAppManifest.DisplayMode.STANDALONE,
                scope = defaultScope(url),
                icons = listOf(WebAppManifest.Icon(src = "$origin/favicon.ico")),
            ),
            fallbackIcon,
        )
    }

    private suspend fun install(manifest: WebAppManifest, fallbackIcon: Bitmap?) {
        val previous = dao.get(manifest.startUrl)
        manifestStorage.saveManifest(manifest)
        dao.put(manifest.toInstalledApp(previous).copy(enabled = true))
        val icon = loadIcon(manifest.startUrl) ?: fallbackIcon
        runCatching { pinShortcut(manifest, icon) }
    }

    suspend fun loadIcon(url: String): Bitmap? {
        val manifest = manifestStorage.loadManifest(url)
        val origin = origin(url)
        val resources = buildList {
            manifest?.icons?.forEach { icon ->
                add(IconRequest.Resource(URI(url).resolve(icon.src).toString(),
                    IconRequest.Resource.Type.MANIFEST_ICON))
            }
            add(IconRequest.Resource("$origin/apple-touch-icon.png", IconRequest.Resource.Type.APPLE_TOUCH_ICON))
            add(IconRequest.Resource("$origin/favicon.ico", IconRequest.Resource.Type.FAVICON))
        }
        return withTimeoutOrNull(5_000) {
            runCatching {
                icons.loadIcon(IconRequest(url, size = IconRequest.Size.LAUNCHER, resources = resources)).await()
            }
                .getOrNull()?.takeUnless { it.source == Icon.Source.GENERATOR }?.bitmap
        }
    }

    suspend fun updateManifest(manifest: WebAppManifest) {
        val previous = dao.get(manifest.startUrl) ?: return
        manifestStorage.updateManifest(manifest)
        dao.put(manifest.toInstalledApp(previous))
        val icon = loadIcon(manifest.startUrl)
        withContext(Dispatchers.Main) {
            runCatching { ShortcutManagerCompat.updateShortcuts(context, listOf(buildShortcut(manifest, icon))) }
        }
    }

    suspend fun setEnabled(app: InstalledWebApp, enabled: Boolean) {
        dao.setEnabled(app.startUrl, enabled)
    }

    suspend fun uninstall(app: InstalledWebApp) {
        dao.delete(app.startUrl)
        manifestStorage.removeManifests(listOf(app.startUrl))
    }

    private suspend fun pinShortcut(manifest: WebAppManifest, bitmap: Bitmap?) = withContext(Dispatchers.Main) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return@withContext
        ShortcutManagerCompat.requestPinShortcut(context, buildShortcut(manifest, bitmap), null)
    }

    private fun buildShortcut(manifest: WebAppManifest, bitmap: Bitmap?): ShortcutInfoCompat {
        val intent = Intent(context, WebAppActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(manifest.startUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }
        return ShortcutInfoCompat.Builder(context, manifest.startUrl)
            .setShortLabel((manifest.shortName ?: manifest.name).ifBlank { context.getString(R.string.app_name) })
            .setLongLabel(manifest.name)
            .setIntent(intent)
            .setIcon(
                bitmap?.takeUnless { it.isRecycled }?.let {
                    IconCompat.createWithBitmap(it.copy(it.config ?: Bitmap.Config.ARGB_8888, false))
                } ?: IconCompat.createWithResource(context, R.mipmap.ic_launcher)
            )
            .build()
    }

    private fun WebAppManifest.toInstalledApp(previous: InstalledWebApp?): InstalledWebApp =
        InstalledWebApp(
            startUrl = startUrl,
            name = name,
            scope = scope?.let { URI(startUrl).resolve(it).toString() } ?: defaultScope(startUrl),
            displayMode = display.name,
            installedAt = previous?.installedAt ?: System.currentTimeMillis(),
            enabled = previous?.enabled ?: true,
        )

    companion object {
        private fun origin(url: String): String = URI(url).let {
            "${it.scheme}://${it.host}${if (it.port >= 0) ":${it.port}" else ""}"
        }

        fun defaultScope(startUrl: String): String = URI(startUrl).resolve(".").toString()

        fun isWithinScope(url: String, scope: String): Boolean = try {
            val target = URI(url)
            val allowed = URI(scope)
            val scopePath = allowed.path.ifEmpty { "/" }
            val pathMatches = if (scopePath.endsWith('/')) {
                target.path.startsWith(scopePath)
            } else {
                target.path == scopePath || target.path.startsWith("$scopePath/")
            }
            (target.scheme.equals("http", ignoreCase = true) ||
                target.scheme.equals("https", ignoreCase = true)) &&
                target.scheme.equals(allowed.scheme, ignoreCase = true) &&
                target.host != null && target.host.equals(allowed.host, ignoreCase = true) &&
                effectivePort(target) == effectivePort(allowed) && pathMatches
        } catch (_: Exception) {
            false
        }

        private fun effectivePort(uri: URI): Int = when {
            uri.port >= 0 -> uri.port
            uri.scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }
    }
}
