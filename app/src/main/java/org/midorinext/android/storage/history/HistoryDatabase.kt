package org.midorinext.android.storage.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


@Database(
    entities = [Page::class, Visit::class, HistoryRemoval::class],
    version = 2
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
                        override fun migrate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE visits_new (pageUri TEXT NOT NULL, time INTEGER NOT NULL, type TEXT NOT NULL, timestampUsec INTEGER NOT NULL, count INTEGER NOT NULL, PRIMARY KEY(pageUri, timestampUsec, type), FOREIGN KEY(pageUri) REFERENCES pages(uri) ON DELETE CASCADE)")
                            db.execSQL("INSERT INTO visits_new SELECT pageUri, time, type, time * 1000, 1 FROM visits")
                            db.execSQL("DROP TABLE visits")
                            db.execSQL("ALTER TABLE visits_new RENAME TO visits")
                            db.execSQL("CREATE TABLE sync_history_removals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, url TEXT, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL)")
                        }
                    }

        private const val name = "history_db"
        @Volatile private var instance: HistoryDatabase? = null

        fun create(context: Context): HistoryDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, HistoryDatabase::class.java, name)
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
