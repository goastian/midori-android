package org.midorinext.android

import android.app.Application
import org.midorinext.android.adblock.MidoriPrivacyFeature
import org.midorinext.android.migration.MigrationUtility
import org.midorinext.android.mozac.GeckoPreferences
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppTrackingProtectionMode
import org.midorinext.android.apptracking.AppTrackingProtectionController
import org.midorinext.android.storage.autofill.AutofillPreferenceState
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.session.SessionUseCases
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.geckoview.GeckoRuntime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MidoriApplication : Application() {
    @Inject lateinit var engine: dagger.Lazy<Engine>
    @Inject lateinit var client: dagger.Lazy<Client>
    @Inject lateinit var store: dagger.Lazy<BrowserStore>
    @Inject lateinit var sessionStorage: dagger.Lazy<SessionStorage>
    @Inject lateinit var tabsUseCases: dagger.Lazy<TabsUseCases>
    @Inject lateinit var sessionUseCases: dagger.Lazy<SessionUseCases>
    @Inject lateinit var MidoriUseCases: dagger.Lazy<MidoriUseCases>
    @Inject lateinit var migrationUtility: dagger.Lazy<MigrationUtility>
    @Inject lateinit var mediaFeature: dagger.Lazy<MediaSessionFeature>
    @Inject lateinit var adBlockerFeature: dagger.Lazy<MidoriPrivacyFeature>
    @Inject lateinit var appTrackingProtectionController: dagger.Lazy<AppTrackingProtectionController>
    @Inject lateinit var geckoRuntime: dagger.Lazy<GeckoRuntime>
    @Inject lateinit var appPreferencesRepository: dagger.Lazy<AppPreferencesRepository>
    @Inject lateinit var autofillPreferenceState: dagger.Lazy<AutofillPreferenceState>

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        setupLogging()
        RustHttpConfig.setClient(lazy { client.get() })

        if (!isMainProcess()) {
            // If this is not the main process then do not continue with the initialization here. Everything that
            // follows only needs to be done in our app's main process and should not be done in other processes like
            // a GeckoView child process or the crash handling process. Most importantly we never want to end up in a
            // situation where we create a GeckoRuntime from the Gecko child process (
            return
        }

        // Apply safe defaults immediately; persisted settings are applied below without blocking startup.
        GeckoPreferences.initialize(geckoRuntime.get(), AppPreferencesSerializer.defaultValue.toGeckoSettings())

        // Watch for preference changes and apply them without requiring restart
        applicationScope.launch(Dispatchers.IO) {
            appPreferencesRepository.get().flow.collect { prefs ->
                autofillPreferenceState.get().update(prefs)
                GeckoPreferences.initialize(geckoRuntime.get(), prefs.toGeckoSettings())

                val shouldRunSystemProtection =
                    prefs.appTrackingProtectionMode == AppTrackingProtectionMode.HYBRID_SYSTEM &&
                        prefs.appTrackingSystemEnabled
                val running = appTrackingProtectionController.get().systemProtectionRunning.value

                if (shouldRunSystemProtection && !running) {
                    appTrackingProtectionController.get().startSystemProtection()
                } else if (!shouldRunSystemProtection && running) {
                    appTrackingProtectionController.get().stopSystemProtection()
                }
            }
        }

        engine.get().apply {
            warmUp()
            speculativeConnect(BuildConfig.QWANT_BASE_URL)
        }

        restoreBrowserState().invokeOnCompletion {
            android.util.Log.d("MidoriPrivacy", "restore tabs done")
            store.get().state.selectedTab?.content?.url?.let {
                android.util.Log.d("MidoriPrivacy", "selected tab url is $it")
                adBlockerFeature.get().restoreTabsDone(it)
            } ?: android.util.Log.d("MidoriPrivacy", "selected tab url is still null after restore")
        }

        migrationUtility.get().checkMigrations()


        mediaFeature.get().start()

        // TODO
        //  Should be removed in futur version, once mozilla has fully migrated
        //  meanwhile move this elsewhere
        val tuc = tabsUseCases.get()
        WebExtensionSupport.initialize(
            runtime = engine.get(),
            store = store.get(),
            openPopupInTab = true,
            onNewTabOverride = { _, engineSession, url ->
                val tabId = tuc.addTab(
                    url = url,
                    selectTab = true,
                    engineSession = engineSession
                )
                tabId
            },
            onCloseTabOverride = { _, sessionId ->
                tuc.removeTab(sessionId)
            },
            onSelectTabOverride = { _, sessionId ->
                tuc.selectTab(sessionId)
            },
            onExtensionsLoaded = {}
        )
    }

    private fun restoreBrowserState() = applicationScope.launch(Dispatchers.Main) {
        sessionStorage.get().let {
            tabsUseCases.get().restore(it)
            // Now that we have restored our previous state (if there's one) let's setup auto saving the state while the app is used.
            it.autoSave(store.get())
                .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
                .whenGoingToBackground()
                .whenSessionsChange()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            store.get().dispatch(SystemAction.LowMemoryAction(level))
        }
    }
}

private fun AppPreferences.toGeckoSettings() = GeckoPreferences.UserSettings(
    globalPrivacyControl = privacyGlobalPrivacyControl,
    fingerprintingProtection = privacyFingerprintingProtection,
    cookiePartitioning = privacyCookiePartitioning,
    strictTrackingProtection = privacyStrictTrackingProtection,
    trackingProtectionLevel = trackingProtectionLevel,
    httpsOnlyLevel = httpsOnlyLevel,
    dohProvider = dohProvider,
    appTrackingProtectionMode = appTrackingProtectionMode
)

private fun setupLogging() {
    // We want the log messages of all builds to go to Android logcat
    Log.addSink(AndroidLogSink())
}
