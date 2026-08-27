package org.midorinext.android.mozac.downloads

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.midorinext.android.preferences.app.AppPreferencesRepository
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

private fun defaultDownloadDirectory(): String =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path

/**
 * Resolves a saved Storage Access Framework tree URI to a usable download location.
 *
 * A tree permission may be revoked at any time by Android or the document provider. In that case
 * downloads must continue in the system Downloads directory instead of failing against a stale URI.
 */
fun Context.resolveDownloadDirectory(savedDirectoryUri: String): String =
    savedDirectoryUri.takeIf { hasPersistedWritableTreePermission(it) }
        ?: defaultDownloadDirectory()

fun Context.hasPersistedWritableTreePermission(uriString: String): Boolean = runCatching {
    val uri = Uri.parse(uriString)
    uri.scheme == "content" &&
        DocumentsContract.isTreeUri(uri) &&
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && permission.isWritePermission
        }
}.getOrDefault(false)

/**
 * Keeps Firefox Android Components' filename resolver aligned with Midori's selected destination.
 * Collision detection happens while response headers are processed, before the file is created.
 */
@Singleton
class DownloadLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    appPreferencesRepository: AppPreferencesRepository,
) {
    private val currentDirectory = AtomicReference(defaultDownloadDirectory())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            appPreferencesRepository.flow.collect { preferences ->
                currentDirectory.set(context.resolveDownloadDirectory(preferences.downloadDirectoryUri))
            }
        }
    }

    fun currentDirectory(): String = currentDirectory.get()

    fun resolve(savedDirectoryUri: String): String = context.resolveDownloadDirectory(savedDirectoryUri)
}
