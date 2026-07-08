package org.midorinext.android.storage.bookmarks

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


@Database(
    entities = [BookmarkNode::class],
    version = 1
)
abstract class BookmarksDatabase : RoomDatabase() {
    abstract fun bookmarksDao(): BookmarksDao

    companion object {
        private const val name = "bookmarks_db"
        @Volatile private var instance: BookmarksDatabase? = null

        fun create(context: Context): BookmarksDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, BookmarksDatabase::class.java, name)
                    .build()
                    .also { instance = it }
            }
        }
    }
}