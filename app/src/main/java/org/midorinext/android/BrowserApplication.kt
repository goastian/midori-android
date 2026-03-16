/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android

import android.app.Application
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.concept.engine.webextension.isUnsupported
import mozilla.components.concept.push.PushProcessor
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.AppServicesInitializer
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.webextensions.WebExtensionSupport
import androidx.preference.PreferenceManager
import org.midorinext.android.push.PushFxaIntegration
import org.midorinext.android.push.WebPushEngineIntegration
import org.midorinext.android.search.AstianGoSearchEngine
import org.midorinext.android.settings.CustomizeSettingsFragment
import java.util.concurrent.TimeUnit
import mozilla.components.support.AppServicesInitializer.Config as AppServicesConfig

open class BrowserApplication : Application() {
    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()

        setupCrashReporting(this)

        // Apply saved theme preference
        val themeValue = PreferenceManager.getDefaultSharedPreferences(this)
            .getString(getString(R.string.pref_key_theme), "system") ?: "system"
        CustomizeSettingsFragment.applyTheme(themeValue)

        AppServicesInitializer.init(
            AppServicesConfig(components.analytics.crashReporter),
        )
        RustHttpConfig.setClient(lazy { components.core.client })

        Log.addSink(AndroidLogSink())

        if (!isMainProcess()) {
            return
        }

        // Engine warmUp must run on main thread (GeckoRuntime requires UI thread)
        components.core.engine.warmUp()

        // Register autocomplete storage delegate for login/card/address autofill in web forms
        components.core.registerAutocompleteDelegate()

        restoreBrowserState()

        // Phase 1: Search engine install (immediate, lightweight)
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.Main) {
            AstianGoSearchEngine.install(this@BrowserApplication, components.core.store)
        }

        // Phase 2: Addon/extension init — deferred 1.5s to let Activity render first
        Handler(Looper.getMainLooper()).postDelayed({
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.Main) {
                initAddonsAndExtensions()
            }
        }, 1500L)

        // Push services — must run immediately so PushProcessor is available
        // before Firebase triggers onNewToken
        initPushServices()

        // Clean uploads directory on background thread
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            components.core.fileUploadsDirCleaner.cleanUploadsDirectory()
        }
    }

    private fun initAddonsAndExtensions() {
        GlobalAddonDependencyProvider.initialize(
            components.core.addonManager,
            components.core.addonUpdater,
        )
        WebExtensionSupport.initialize(
            runtime = components.core.engine,
            store = components.core.store,
            onNewTabOverride = { _, engineSession, url ->
                val tabId = components.useCases.tabsUseCases.addTab(
                    url = url,
                    selectTab = true,
                    engineSession = engineSession,
                )
                tabId
            },
            onCloseTabOverride = { _, sessionId ->
                components.useCases.tabsUseCases.removeTab(sessionId)
            },
            onSelectTabOverride = { _, sessionId ->
                components.useCases.tabsUseCases.selectTab(sessionId)
            },
            onExtensionsLoaded = { extensions ->
                components.core.addonUpdater.registerForFutureUpdates(extensions)

                val checker = components.core.supportedAddonsChecker
                val hasUnsupportedAddons = extensions.any { it.isUnsupported() }
                if (hasUnsupportedAddons) {
                    checker.registerForChecks()
                } else {
                    checker.unregisterForChecks()
                }
            },
            onUpdatePermissionRequest = components.core.addonUpdater::onUpdatePermissionRequest,
        )
    }

    private fun initPushServices() {
        components.push.feature?.let {
            Logger.info("AutoPushFeature is configured, initializing it...")

            PushProcessor.install(it)

            WebPushEngineIntegration(components.core.engine, it).start()
            PushFxaIntegration(it, lazy { components.backgroundServices.accountManager }).launch()
            it.initialize()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            components.core.store.dispatch(SystemAction.LowMemoryAction(level))
            components.core.icons.onTrimMemory(level)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun restoreBrowserState() =
        GlobalScope.launch(Dispatchers.Main) {
        val store = components.core.store
        val sessionStorage = components.core.sessionStorage

        components.useCases.tabsUseCases.restore(sessionStorage)

        // Now that we have restored our previous state (if there's one) let's setup auto saving the state while
        // the app is used.
        sessionStorage
            .autoSave(store)
            .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
            .whenGoingToBackground()
            .whenSessionsChange()
    }

    companion object {
        const val NON_FATAL_CRASH_BROADCAST = "org.midorinext.android"
    }
}

private fun setupCrashReporting(application: BrowserApplication) {
    application
        .components
        .analytics
        .crashReporter
        .install(application)
}
