package org.midorinext.android.storage.readinglist

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ReadingListHiltModule {
    @Singleton
    @Provides
    fun provideReadingListDatabase(@ApplicationContext context: Context): ReadingListDatabase {
        return ReadingListDatabase.create(context)
    }
}
