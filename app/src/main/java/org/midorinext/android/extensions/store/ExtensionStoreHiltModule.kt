package org.midorinext.android.extensions.store

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
object ExtensionStoreHiltModule {
    @Singleton
    @Provides
    fun provideExtensionDataStore(
        @ApplicationContext context: Context
    ): DataStore<ExtensionStorePreferences> {
        return ExtensionStoreFactory.create(context)
    }
}

