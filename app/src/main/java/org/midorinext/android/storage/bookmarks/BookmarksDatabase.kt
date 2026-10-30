package org.midorinext.android.storage.bookmarks

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.midorinext.android.sync.*
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(
    entities = [BookmarkNode::class, SyncRevision::class, SyncLocalChange::class, SyncRecordIndex::class],
    version = 3
)
abstract class BookmarksDatabase : RoomDatabase() {
    abstract fun bookmarksDao(): BookmarksDao
    internal abstract fun syncJournalDao(): SyncJournalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) { SyncJournalSchema.create(db, history = false) }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) { SyncJournalSchema.create(db, history = false) }
        }
        private const val name = "bookmarks_db"
        @Volatile private var instance: BookmarksDatabase? = null

        fun create(context: Context): BookmarksDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, BookmarksDatabase::class.java, name)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(SyncJournalSchema.callback(history = false))
                    .build()
                    .also { instance = it }
            }
        }
    }
}