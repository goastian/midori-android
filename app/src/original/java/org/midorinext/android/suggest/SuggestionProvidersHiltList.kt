package org.midorinext.android.suggest

import org.midorinext.android.storage.bookmarks.BookmarksRepository
import org.midorinext.android.storage.history.HistoryRepository
import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
import org.midorinext.android.suggest.providers.SessionTabsProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@InstallIn(SingletonComponent::class)
@Module
object SuggestionProvidersHiltModule {
    @Provides fun provideSuggestionProviders(
        clipboardProvider: ClipboardProvider,
        midoriSuggestProvider: MidoriSuggestProvider,
        domainProvider: DomainProvider,
        sessionTabsProvider: SessionTabsProvider,
        historyRepository: HistoryRepository,
        bookmarksRepository: BookmarksRepository
    ): List<SuggestionProvider> {
        return listOf(
            clipboardProvider,
            midoriSuggestProvider,
            domainProvider,
            sessionTabsProvider,
            historyRepository,
            bookmarksRepository
        )
    }
}