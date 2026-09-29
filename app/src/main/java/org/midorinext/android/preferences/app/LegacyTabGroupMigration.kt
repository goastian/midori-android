package org.midorinext.android.preferences.app

import android.content.Context
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStreamReader
import java.util.Locale

private const val LEGACY_GROUP_PARTITION = "TAB_GROUPS"
private const val LOG_TAG = "MidoriTabGroups"

/** Move groups out of Mozilla's removed tab partition state before its session file is rewritten. */
suspend fun migrateLegacyTabGroups(
    context: Context,
    engineName: String,
    repository: AppPreferencesRepository,
) {
    if (repository.flow.first().tabGroupsMigrated) return

    val sessionFile = File(
        context.filesDir,
        "mozilla_components_session_storage_${engineName.lowercase(Locale.ROOT)}.json",
    )
    val groups = try {
        withContext(Dispatchers.IO) {
            if (!sessionFile.exists()) emptyList() else sessionFile.inputStream().use { input ->
                JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readLegacyGroups() }
            }
        }
    } catch (error: Exception) {
        Log.w(LOG_TAG, "Could not read legacy tab groups", error)
        return
    }

    repository.migrateLegacyTabGroups(groups)
}

private fun JsonReader.readLegacyGroups(): List<SavedTabGroup> {
    beginObject()
    var groups = emptyList<SavedTabGroup>()
    while (hasNext()) {
        when (nextName()) {
            "tabPartitions" -> {
                beginArray()
                while (hasNext()) {
                    beginObject()
                    var partitionId = ""
                    var partitionGroups = emptyList<SavedTabGroup>()
                    while (hasNext()) {
                        when (nextName()) {
                            "id" -> partitionId = nextString()
                            "tabGroups" -> {
                                beginArray()
                                partitionGroups = buildList {
                                    while (hasNext()) readLegacyGroup()?.let(::add)
                                }
                                endArray()
                            }
                            else -> skipValue()
                        }
                    }
                    endObject()
                    if (partitionId == LEGACY_GROUP_PARTITION) groups = partitionGroups
                }
                endArray()
            }
            else -> skipValue()
        }
    }
    endObject()
    return groups
}

private fun JsonReader.readLegacyGroup(): SavedTabGroup? {
    beginObject()
    var id = ""
    var name = ""
    val tabIds = mutableListOf<String>()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextString()
            "name" -> name = nextString()
            "tabIds" -> {
                beginArray()
                while (hasNext()) tabIds.add(nextString())
                endArray()
            }
            else -> skipValue()
        }
    }
    endObject()
    if (id.isBlank() || tabIds.size < 2) return null
    return SavedTabGroup.newBuilder()
        .setId(id)
        .setName(name)
        .addAllTabIds(tabIds)
        .build()
}
