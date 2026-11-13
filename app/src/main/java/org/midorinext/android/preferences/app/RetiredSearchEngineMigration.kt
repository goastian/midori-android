package org.midorinext.android.preferences.app

import androidx.datastore.core.DataMigration
import org.midorinext.android.usecases.SearchEngines

object RetiredSearchEngineMigration : DataMigration<AppPreferences> {
    override suspend fun shouldMigrate(currentData: AppPreferences): Boolean =
        currentData.searchEngine != SearchEngines.resolve(currentData.searchEngine) ||
            currentData.privateSearchEngine != SearchEngines.resolve(currentData.privateSearchEngine) ||
            "astiango" in currentData.hiddenSearchEnginesList

    override suspend fun migrate(currentData: AppPreferences): AppPreferences = currentData.toBuilder()
        .setSearchEngine(SearchEngines.resolve(currentData.searchEngine))
        .setPrivateSearchEngine(SearchEngines.resolve(currentData.privateSearchEngine))
        .clearHiddenSearchEngines()
        .addAllHiddenSearchEngines(currentData.hiddenSearchEnginesList.filterNot { it == "astiango" })
        .build()

    override suspend fun cleanUp() = Unit
}
