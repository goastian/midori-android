package org.midorinext.android.migration.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile

object MigrationDataFactory {
    private const val FILENAME = "migrations_data.pb"

    fun create(context: Context): DataStore<MigrationData> {
        return DataStoreFactory.create(
            serializer = MigrationDataSerializer,
            produceFile = { context.dataStoreFile(FILENAME) }
        )
    }
}