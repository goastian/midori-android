package org.midorinext.android.mozac.hilt

import org.midorinext.android.AppRequestInterceptor
import org.midorinext.android.BuildConfig
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Settings
import mozilla.components.concept.storage.HistoryStorage
import mozilla.components.feature.session.HistoryDelegate
import org.mozilla.geckoview.GeckoRuntimeSettings
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object GeckoSettingsHiltModule {
    private val debugEnabled = BuildConfig.DEBUG

    @Singleton
    @Provides
    fun provideGeckoRuntimeSettings(
    ) : GeckoRuntimeSettings {
        val builder = GeckoRuntimeSettings.Builder()

        // Debug settings
        builder.javaScriptEnabled(true)
        builder.aboutConfigEnabled(debugEnabled)
        builder.consoleOutput(debugEnabled)
        builder.debugLogging(debugEnabled)
        builder.remoteDebuggingEnabled(debugEnabled)

        return builder.build()
    }


    @Singleton
    @Provides
    fun provideEngineSettings(
        appRequestInterceptor: AppRequestInterceptor,
        historyStorage: Lazy<HistoryStorage>
    ) : Settings {
        return DefaultSettings(
            historyTrackingDelegate = HistoryDelegate(lazy { historyStorage.get() }),
            requestInterceptor = appRequestInterceptor,
            userAgentString = org.mozilla.geckoview.BuildConfig.USER_AGENT_GECKOVIEW_MOBILE,
            remoteDebuggingEnabled = debugEnabled,
            testingModeEnabled = debugEnabled
        )
    }
}