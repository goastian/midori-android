package org.midorinext.android.mozac.hilt


import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.contextmenu.ContextMenuUseCases
import mozilla.components.feature.downloads.DownloadsUseCases
import mozilla.components.feature.pwa.WebAppShortcutManager
import mozilla.components.feature.pwa.WebAppUseCases
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.utils.DownloadFileUtils

@InstallIn(SingletonComponent::class)
@Module
object MozacUseCasesHiltModule {
    @Provides fun provideSessionUseCases(store: BrowserStore): SessionUseCases {
        return SessionUseCases(store)
    }
    @Provides fun provideTabsUseCases(store: BrowserStore): TabsUseCases {
        return TabsUseCases(store)
    }
    @Provides fun provideContextMenuUseCases(store: BrowserStore): ContextMenuUseCases {
        return ContextMenuUseCases(store)
    }
    @Provides fun provideDownloadsUseCases(store: BrowserStore, downloadFileUtils: DownloadFileUtils): DownloadsUseCases {
        return DownloadsUseCases(store, downloadFileUtils)
    }
    @Provides fun provideWebAppUseCases(
        @ApplicationContext context: Context,
        store: BrowserStore,
        shortcutManager: WebAppShortcutManager
    ): WebAppUseCases {
        return WebAppUseCases(context, store, shortcutManager)
    }
}