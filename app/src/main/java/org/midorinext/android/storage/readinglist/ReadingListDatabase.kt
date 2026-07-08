package org.midorinext.android.storage.readinglist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingListItem::class],
    version = 1
)
abstract class ReadingListDatabase : RoomDatabase() {
    abstract fun readingListDao(): ReadingListDao

    companion object {
        private const val name = "reading_list_db"
        @Volatile private var instance: ReadingListDatabase? = null

        fun create(context: Context): ReadingListDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, ReadingListDatabase::class.java, name)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
