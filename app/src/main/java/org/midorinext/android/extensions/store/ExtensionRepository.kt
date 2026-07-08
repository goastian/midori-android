package org.midorinext.android.extensions.store

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

private const val TAG = "ExtensionRepository"

class ExtensionRepository @Inject constructor(
    private val datastore: DataStore<ExtensionStorePreferences>
) {
    val extensionsFlow: Flow<List<InstalledExtensionProto>> = datastore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading extension preferences.", exception)
                emit(ExtensionStorePreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }
        .map { it.extensionsList }

    suspend fun addExtension(extension: InstalledExtensionProto) {
        datastore.updateData { prefs ->
            val filtered = prefs.extensionsList.filter { it.id != extension.id }
            prefs.toBuilder()
                .clearExtensions()
                .addAllExtensions(filtered + extension)
                .build()
        }
    }

    suspend fun removeExtension(extensionId: String) {
        datastore.updateData { prefs ->
            prefs.toBuilder()
                .clearExtensions()
                .addAllExtensions(prefs.extensionsList.filter { it.id != extensionId })
                .build()
        }
    }

    suspend fun setEnabled(extensionId: String, enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder()
                .clearExtensions()
                .addAllExtensions(prefs.extensionsList.map {
                    if (it.id == extensionId) it.toBuilder().setEnabled(enabled).build() else it
                })
                .build()
        }
    }
}

