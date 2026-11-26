package org.midorinext.android.mozac.hilt

import android.content.Context
import org.midorinext.android.AppRequestInterceptor
import org.midorinext.android.BuildConfig
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.DownloadDelegate
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.session.HistoryDelegate
import mozilla.components.lib.crash.handler.CrashHandlerService
import org.mozilla.geckoview.GeckoRuntimeSettings
import mozilla.components.support.utils.DownloadFileUtils
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object GeckoSettingsHiltModule {
    private val debugEnabled = BuildConfig.DEBUG

    @Singleton
    @Provides
    fun provideGeckoRuntimeSettings(
        @ApplicationContext context: Context,
    ) : GeckoRuntimeSettings {
        val builder = GeckoRuntimeSettings.Builder()

        // Debug settings
        builder.javaScriptEnabled(true)
        builder.aboutConfigEnabled(debugEnabled)
        builder.consoleOutput(debugEnabled)
        builder.debugLogging(debugEnabled)
        builder.remoteDebuggingEnabled(debugEnabled)
        builder.loginAutofillEnabled(false)
        org.midorinext.android.mozac.Gecko161Features.proguardUuid(
            builder,
            org.midorinext.android.diagnostics.GeckoBuildMetadata.proguardUuid(context),
        )
        builder.crashHandler(CrashHandlerService::class.java)

        return builder.build()
    }


    @Singleton
    @Provides
    fun provideEngineSettings(
        appRequestInterceptor: AppRequestInterceptor,
        historyStorage: Lazy<HistoryStorage>,
        downloadFileUtils: DownloadFileUtils,
    ) : Settings {
        return DefaultSettings(
            historyTrackingDelegate = HistoryDelegate(lazy { historyStorage.get() }),
            downloadDelegate = object : DownloadDelegate {
                override fun guessFileName(
                    contentDisposition: String?,
                    url: String?,
                    mimeType: String?,
                ): String = downloadFileUtils.guessFileName(contentDisposition, url, mimeType)
            },
            requestInterceptor = appRequestInterceptor,
            remoteDebuggingEnabled = debugEnabled,
            testingModeEnabled = debugEnabled,
            // Sessions lose their display when the user opens the tab tray, leaves the app, or
            // locks the device. Keep active media running across those transitions.
            suspendMediaWhenInactive = false,
        )
    }
}
