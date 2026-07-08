package org.midorinext.android.contentBlocker

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ContentBlockerHiltModule {
    @Singleton
    @Provides
    fun provideContentBlockerState() : ContentBlockerState {
        return ContentBlockerState()
    }
}