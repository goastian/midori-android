package org.midorinext.android.contentBlocker.cacheDb

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [Domain::class, Url::class, TabId::class],
    version = 1
)
abstract class ContentBlockerCacheDatabase: RoomDatabase() {
    abstract fun contentBlockerCacheDao(): ContentBlockerCacheDao

    companion object {
        private const val name = "contentblocker_cache_db"
        @Volatile private var instance: ContentBlockerCacheDatabase? = null

        fun create(context: Context): ContentBlockerCacheDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, ContentBlockerCacheDatabase::class.java, name)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ContentBlockerCacheHiltModule {
    @Singleton
    @Provides
    fun provideContentBlockerCacheDatabase(@ApplicationContext context: Context)
            : ContentBlockerCacheDatabase {
        return ContentBlockerCacheDatabase.create(context)
    }
}