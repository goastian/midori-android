package org.midorinext.android.migration

import android.content.Context
import android.util.Log
import org.midorinext.android.migration.datastore.MigrationData
import org.midorinext.android.migration.datastore.MigrationDataRepository
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import org.midorinext.android.storage.history.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import org.mozilla.reference.browser.storage.BookmarkItemV2
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.ObjectInputStream
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MigrationUtility @Inject constructor(
    @ApplicationContext private val context: Context,
    private val migrationDataRepository: MigrationDataRepository,
    private val historyRepository: HistoryRepository,
    private val bookmarksRepository: BookmarksRepository
) {
    fun checkMigrations() {
        MainScope().launch {
            val migrationData = migrationDataRepository.flow.first()
            migration504(migrationData)
        }
    }

    private suspend fun migration504(migrationData: MigrationData) {
        if (!migrationData.migration504History) {
            migrateHistoryV4()
        }
        if (!migrationData.migration504Bookmarks) {
            migrateBookmarksV4()
        }
    }

    private suspend fun migrateHistoryV4() = withContext(Dispatchers.IO) {
        val historyV4Filename = "midori_history"
        var file: File? = null
        var fileInputStream: FileInputStream? = null
        var objectInputStream: ObjectInputStream? = null
        var shouldDeleteFile = false

        try {
            file = context.getFileStreamPath(historyV4Filename)
            if (file.exists()) {
                fileInputStream = context.openFileInput(historyV4Filename)
                objectInputStream = ObjectInputStream(fileInputStream)
                val pages = objectInputStream.readObject() as HashMap<*, *>
                val pageMeta = objectInputStream.readObject() as HashMap<*, *>
                pages.forEach { map ->
                    val url = map.key as String
                    val value = map.value as List<*>
                    value.forEach { v ->
                        val visit = v as org.midorinext.android.legacy.history.Visit
                        historyRepository.recordVisitWithTimestamp(url, PageVisit(visit.type), visit.timestamp)
                    }
                }
                pageMeta.forEach { map ->
                    val url = map.key as String
                    val observation = map.value as org.midorinext.android.legacy.history.PageObservation
                    historyRepository.recordObservation(url, PageObservation(observation.title))
                }
                migrationDataRepository.migration504HistoryDone()
                shouldDeleteFile = true
            } else {
                Log.w("MIDORI_MIGRATE", "No history yet")
                migrationDataRepository.migration504HistoryDone()
            }
        } catch (e: FileNotFoundException) {
            Log.e("MIDORI_MIGRATE", "Failed reading history v4 file: File not found: ", e)
        } catch (e: IOException) {
            Log.e("MIDORI_MIGRATE", "Failed reading history v4 file: IO exception: ", e)
        } catch (e: ClassNotFoundException) {
            Log.e("MIDORI_MIGRATE", "Failed reading history v4 file: Class not found: ", e)
        } catch (e: Exception) {
            Log.e("MIDORI_MIGRATE", "Failed reading history v4 file: ", e)
        } finally {
            objectInputStream?.close()
            fileInputStream?.close()
            if (shouldDeleteFile) { file?.delete() }
        }
    }

    private suspend fun migrateBookmarksV4() = withContext(Dispatchers.IO) {
        val bookmarksV4Filename = "midori_bookmarks"
        var file: File? = null
        var fileInputStream: FileInputStream? = null
        var objectInputStream: ObjectInputStream? = null
        var shouldDeleteFile = false

        try {
            file = context.getFileStreamPath(bookmarksV4Filename)
            if (file.exists()) {
                fileInputStream = context.openFileInput(bookmarksV4Filename)
                objectInputStream = ObjectInputStream(fileInputStream)
                val bookmarksList = objectInputStream.readObject() as ArrayList<*>
                bookmarksList.forEach {
                    this@MigrationUtility.restoreBookmarkV4Recursive(it as BookmarkItemV2, bookmarksRepository.root.guid)
                }
                migrationDataRepository.migration504BookmarksDone()
                shouldDeleteFile = true
            } else {
                migrationDataRepository.migration504BookmarksDone()
                Log.w("MIDORI_MIGRATE" ,"No bookmarks yet")
            }
        } catch (e: FileNotFoundException) {
            Log.e("MIDORI_MIGRATE", "Failed reading history file: File not found: " + e.message)
            // e.printStackTrace()
        } catch (e: IOException) {
            Log.e("MIDORI_MIGRATE", "Failed reading bookmarks file: IO exception: " + e.message)
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            Log.e("MIDORI_MIGRATE", "Failed reading bookmarks file: Class not found: " + e.message)
            e.printStackTrace()
        } catch (e: Exception) {
            Log.e("MIDORI_MIGRATE", "Failed reading bookmarks file: " + e.message)
            e.printStackTrace()
        } finally {
            objectInputStream?.close()
            fileInputStream?.close()
            if (shouldDeleteFile) { file?.delete() }
        }
    }

    private suspend fun restoreBookmarkV4Recursive(bookmark: BookmarkItemV2, parentGuid: String) {
        when (bookmark.type) {
            BookmarkItemV2.BookmarkType.BOOKMARK -> {
                bookmarksRepository.addItem(parentGuid, bookmark.url ?: "", bookmark.title, position = null)
            }
            BookmarkItemV2.BookmarkType.FOLDER -> {
                val folderGuid = bookmarksRepository.addFolder(parentGuid, bookmark.title, position = null).getOrNull()
                @Suppress("UNNECESSARY_SAFE_CALL") // null exception needed for very old bookmarks deserialization
                if (folderGuid != null) {
                    bookmark.children?.forEach {
                        this.restoreBookmarkV4Recursive(it, folderGuid)
                    }
                }
            }
        }
    }
}