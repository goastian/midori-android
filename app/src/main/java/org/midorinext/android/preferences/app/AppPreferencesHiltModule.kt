package org.midorinext.android.preferences.app

import android.content.Context
import androidx.datastore.core.DataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
object AppPreferencesHiltModule {
    @Singleton
    @Provides fun provideAppPreferencesDataStore(@ApplicationContext appContext: Context)
    : DataStore<AppPreferences> {
        return AppPreferencesFactory.create(appContext)
    }
}