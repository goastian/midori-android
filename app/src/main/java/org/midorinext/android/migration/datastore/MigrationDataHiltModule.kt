package org.midorinext.android.migration.datastore

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
object MigrationDataHiltModule {
    @Singleton
    @Provides fun provideMigrationDataStore(@ApplicationContext appContext: Context)
    : DataStore<MigrationData> {
        return MigrationDataFactory.create(appContext)
    }
}