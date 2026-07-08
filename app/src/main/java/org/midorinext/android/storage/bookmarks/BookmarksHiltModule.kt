package org.midorinext.android.storage.bookmarks

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mozilla.components.concept.storage.BookmarksStorage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BookmarksHiltModule {
    @Singleton
    @Provides fun provideBookmarksDatabase(@ApplicationContext context: Context)
    : BookmarksDatabase {
        return BookmarksDatabase.create(context)
    }

    @Singleton
    @Provides fun provideBookmarkStorage(
        bookmarksRepository: BookmarksRepository
    ) : BookmarksStorage {
        return bookmarksRepository
    }
}