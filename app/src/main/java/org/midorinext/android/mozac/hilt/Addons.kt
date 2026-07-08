package org.midorinext.android.mozac.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.addons.AddonManager
import mozilla.components.feature.addons.AddonsProvider
import mozilla.components.feature.addons.update.DefaultAddonUpdater
import mozilla.components.support.base.android.NotificationsDelegate
import org.midorinext.android.extensions.AMOSearchAddonsProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AddonsHiltModule {

    /**
     * Provides an [AddonsProvider] backed by the AMO Search API (v5).
     *
     * This returns the full list of recommended Android extensions instead of
     * the limited collection endpoint, giving users access to ~30+ vetted
     * extensions.
     */
    @Singleton
    @Provides
    fun provideAddonsProvider(
        @ApplicationContext context: Context,
        client: Client
    ): AddonsProvider {
        return AMOSearchAddonsProvider(
            context = context,
            client = client,
            maxCacheAgeInMinutes = 7 * 24 * 60
        )
    }

    /**
     * Provides the [AddonManager] that orchestrates install / uninstall /
     * enable / disable of WebExtensions via the [Engine] and keeps the
     * [BrowserStore] in sync.
     */
    @Singleton
    @Provides
    fun provideAddonManager(
        store: BrowserStore,
        engine: Engine,
        addonsProvider: AddonsProvider,
        addonUpdater: DefaultAddonUpdater
    ): AddonManager {
        return AddonManager(store, engine, addonsProvider, addonUpdater)
    }

    @Singleton
    @Provides
    fun provideAddonUpdater(
        @ApplicationContext context: Context,
        notificationsDelegate: NotificationsDelegate
    ): DefaultAddonUpdater {
        return DefaultAddonUpdater(context, notificationsDelegate = notificationsDelegate)
    }
}


