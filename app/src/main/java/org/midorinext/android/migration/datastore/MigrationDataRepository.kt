package org.midorinext.android.migration.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.*
import java.io.IOException
import javax.inject.Inject

private const val LOGTAG: String = "MigrationDataRepo"


class MigrationDataRepository @Inject constructor(
    private val datastore: DataStore<MigrationData>
) {
    val flow: Flow<MigrationData> = datastore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(LOGTAG, "Error reading frontend preferences.", exception)
                emit(MigrationData.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun migration503Done() {
        datastore.updateData { data ->
            data.toBuilder().setMigration503(false).build()
        }
    }

    suspend fun migration504HistoryDone() {
        datastore.updateData { data ->
            data.toBuilder().setMigration504History(true).build()
        }
    }

    suspend fun migration504BookmarksDone() {
        datastore.updateData { data ->
            data.toBuilder().setMigration504Bookmarks(true).build()
        }
    }
}