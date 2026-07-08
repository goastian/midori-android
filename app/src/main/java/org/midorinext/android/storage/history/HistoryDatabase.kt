package org.midorinext.android.storage.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(
    entities = [Page::class, Visit::class],
    version = 1
)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val name = "history_db"
        @Volatile private var instance: HistoryDatabase? = null

        fun create(context: Context): HistoryDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, HistoryDatabase::class.java, name)
                    .build()
                    .also { instance = it }
            }
        }
    }
}