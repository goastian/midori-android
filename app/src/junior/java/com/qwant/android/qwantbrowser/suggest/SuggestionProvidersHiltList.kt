package org.midorinext.android.suggest

import org.midorinext.android.suggest.providers.ClipboardProvider
import org.midorinext.android.suggest.providers.DomainProvider
import org.midorinext.android.suggest.providers.MidoriSuggestProvider
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
        domainProvider: DomainProvider
    ): List<SuggestionProvider> {
        return listOf(
            clipboardProvider,
            midoriSuggestProvider,
            domainProvider
        )
    }
}