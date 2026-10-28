package org.midorinext.android.sync

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.VisitType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.Visit

@RunWith(AndroidJUnit4::class)
class SyncHistoryMigrationTest {
    @Test fun existing_visits_survive_migration_and_keep_microseconds_and_transitions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".synctest"))
        val name = "sync_history_migration_test"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE pages (uri TEXT NOT NULL PRIMARY KEY, title TEXT, previewImageUrl TEXT)")
            db.execSQL("CREATE TABLE visits (pageUri TEXT NOT NULL, time INTEGER NOT NULL, type TEXT NOT NULL, PRIMARY KEY(pageUri,time), FOREIGN KEY(pageUri) REFERENCES pages(uri) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO pages VALUES ('https://migration.example.invalid/','Preserved title',NULL)")
            db.execSQL("INSERT INTO visits VALUES ('https://migration.example.invalid/',1700000000000,'LINK')")
            db.version = 1
        }
        val migrated = Room.databaseBuilder(context, HistoryDatabase::class.java, name).addMigrations(HistoryDatabase.MIGRATION_1_2).build()
        try {
            val dao = migrated.historyDao()
            val original = dao.syncVisits().single()
            assertEquals(1700000000000000, original.timestampUsec)
            assertEquals(1, original.count)
            assertEquals("Preserved title", dao.getPage(original.pageUri)?.title)
            dao.insert(Visit(original.pageUri, original.time, VisitType.TYPED, original.timestampUsec, 2))
            dao.insert(Visit(original.pageUri, original.time, VisitType.LINK, original.timestampUsec + 1, 1))
            assertEquals(3, dao.syncVisits().size)
            assertTrue(dao.hasVisits(original.pageUri))
            dao.deleteAllPages()
            assertTrue(dao.syncVisits().isEmpty())
            assertFalse(dao.hasVisits(original.pageUri))
        } finally { migrated.close(); context.deleteDatabase(name) }
    }
}
