package org.midorinext.android.extensions.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile

object ExtensionStoreFactory {
    private const val FILENAME = "extensions.pb"

    fun create(context: Context): DataStore<ExtensionStorePreferences> {
        return DataStoreFactory.create(
            serializer = ExtensionStoreSerializer,
            produceFile = { context.dataStoreFile(FILENAME) }
        )
    }
}

