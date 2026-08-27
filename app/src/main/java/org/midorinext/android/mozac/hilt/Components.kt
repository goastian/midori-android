package org.midorinext.android.mozac.hilt

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.mozac.downloads.DownloadService
import org.midorinext.android.mozac.media.MediaSessionService
import org.midorinext.android.mozac.pdf.PdfSaveEvents
import org.midorinext.android.mozac.pdf.SaveToPdfMiddleware
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.action.TranslationsAction
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.engine.middleware.TranslationsMiddleware
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.downloads.DefaultFileSizeFormatter
import mozilla.components.feature.downloads.DownloadEstimator
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.downloads.DownloadStorage
import mozilla.components.feature.downloads.FileSizeFormatter
import mozilla.components.feature.downloads.manager.DownloadManager
import mozilla.components.feature.downloads.manager.FetchDownloadManager
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.media.middleware.LastMediaAccessMiddleware
import mozilla.components.feature.media.middleware.RecordingDevicesMiddleware
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.pwa.ManifestStorage
import mozilla.components.feature.pwa.WebAppShortcutManager
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.support.base.android.NotificationsDelegate
import mozilla.components.support.utils.DefaultDateTimeProvider
import mozilla.components.support.utils.DefaultDownloadFileUtils
import mozilla.components.support.utils.DownloadFileUtils
import javax.inject.Singleton


@InstallIn(SingletonComponent::class)
@Module
object MozacComponentHiltModule {
    @Singleton
    @Provides
    fun provideBrowserStore(
        @ApplicationContext context: Context,
        engine: Engine,
        downloadStorage: DownloadStorage,
        downloadFileUtils: DownloadFileUtils,
        thumbnailStorage: ThumbnailStorage,
        notificationsDelegate: NotificationsDelegate,
        appPreferencesRepository: AppPreferencesRepository,
        pdfSaveEvents: PdfSaveEvents,
    ) : BrowserStore {
        val translationsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        return BrowserStore(
            middleware = listOf(
                DownloadMiddleware(
                    context,
                    DownloadService::class.java,
                    deleteFileFromStorage = { false },
                    downloadFileUtils = downloadFileUtils,
                    downloadStorage = downloadStorage
                ),
                SaveToPdfMiddleware(pdfSaveEvents),
                ThumbnailsMiddleware(thumbnailStorage),
                PromptMiddleware(),
                LastAccessMiddleware(),
                UndoMiddleware(clearAfterMillis = 15_000),
                LastMediaAccessMiddleware(),
                RecordingDevicesMiddleware(context, notificationsDelegate)
            ) + EngineMiddleware.create(engine) + TranslationsMiddleware(
                engine = engine,
                // The browser store is application-scoped, so its translation coordinator must
                // outlive individual screens and sessions.
                scope = translationsScope,
                isTranslationsEnabled = { !appPreferencesRepository.flow.first().translationsDisabled }
            )
        ).also { store ->
            // TranslationsMiddleware only sends this command after a user toggles the setting.
            // Send it once at startup as well; otherwise the preference can say "enabled" while
            // GeckoView's native AI feature stays disabled and ignores TranslateAction requests.
            translationsScope.launch {
                store.dispatch(
                    TranslationsAction.SetTranslationsEnabledAction(
                        !appPreferencesRepository.flow.first().translationsDisabled
                    )
                )
            }
        }
    }

    @Singleton
    @Provides
    fun provideThumbnailStorage(
        @ApplicationContext context: Context
    ) : ThumbnailStorage {
        return ThumbnailStorage(context)
    }

    @Singleton
    @Provides
    fun provideDownloadStorage(
        @ApplicationContext context: Context
    ) : DownloadStorage {
        return DownloadStorage(context)
    }

    @Singleton
    @Provides
    fun provideDownloadFileUtils(
        @ApplicationContext context: Context
    ): DownloadFileUtils {
        return DefaultDownloadFileUtils(context)
    }

    @Singleton
    @Provides
    fun provideSessionStorage(
        @ApplicationContext context: Context,
        engine: Engine
    ) : SessionStorage {
        return SessionStorage(context, engine)
    }

    @Singleton
    @Provides
    fun provideBrowserIcons(
        @ApplicationContext context: Context,
        engine: Engine,
        client: Client,
        store: BrowserStore
    ) : BrowserIcons {
        return BrowserIcons(context, client).apply {
            this.install(engine, store)
        }
    }

    @Singleton
    @Provides
    fun provideShortcutManager(
        @ApplicationContext context: Context,
        client: Client,
    ) : WebAppShortcutManager {
        return WebAppShortcutManager(context, client, ManifestStorage(context))
    }

    @Singleton
    @Provides
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        store: BrowserStore,
        notificationsDelegate: NotificationsDelegate
    ) : DownloadManager {
        return FetchDownloadManager(context, store, DownloadService::class,
            notificationsDelegate = notificationsDelegate
        )
    }

    @Singleton
    @Provides
    fun provideNotificationDelegate(
        @ApplicationContext context: Context
    ) : NotificationsDelegate {
        return NotificationsDelegate(NotificationManagerCompat.from(context))
    }

    @Singleton
    @Provides
    fun provideMediaSessionFeature(
        @ApplicationContext context: Context,
        store: BrowserStore,
    ) : MediaSessionFeature {
        return MediaSessionFeature(context, MediaSessionService::class.java, store)
    }

    @Singleton
    @Provides
    fun provideFileSizeFormatter(
        @ApplicationContext context: Context
    ): FileSizeFormatter {
        return DefaultFileSizeFormatter(context)
    }

    @Singleton
    @Provides
    fun provideDownloadEstimator(): DownloadEstimator {
        return DownloadEstimator(DefaultDateTimeProvider())
    }
}
