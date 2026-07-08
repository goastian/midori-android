package org.midorinext.android.contentBlocker

import org.midorinext.android.contentBlocker.cacheDb.ContentBlockerCacheRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mozilla.components.browser.state.store.BrowserStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContentBlockerHiltModule {
    @Singleton
    @Provides
    fun provideContentBlockerState(
        store: BrowserStore,
        contentBlockerService: ContentBlockerService,
        contentBlockerCacheRepository: ContentBlockerCacheRepository
    ) : ContentBlockerState {
        return JuniorContentBlockerState(
            store, contentBlockerService, contentBlockerCacheRepository
        )
    }
}