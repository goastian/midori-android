/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import mozilla.components.browser.domains.autocomplete.BaseDomainAutocompleteProvider
import mozilla.components.browser.domains.autocomplete.ShippedDomainsProvider
import mozilla.components.browser.engine.gecko.GeckoEngine
import mozilla.components.browser.engine.gecko.fetch.GeckoViewFetchClient
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.session.storage.SessionStorage
import mozilla.components.browser.state.engine.EngineMiddleware
import mozilla.components.browser.state.engine.middleware.SessionPrioritizationMiddleware
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.SearchState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.browser.thumbnails.ThumbnailsMiddleware
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.concept.engine.DefaultSettings
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.mediaquery.PreferredColorScheme
import mozilla.components.concept.fetch.Client
import mozilla.components.feature.awesomebar.provider.SessionAutocompleteProvider
import mozilla.components.feature.customtabs.store.CustomTabsServiceStore
import mozilla.components.feature.downloads.DownloadMiddleware
import mozilla.components.feature.logins.exceptions.LoginExceptionStorage
import mozilla.components.feature.media.MediaSessionFeature
import mozilla.components.feature.media.middleware.LastMediaAccessMiddleware
import mozilla.components.feature.media.middleware.RecordingDevicesMiddleware
import mozilla.components.feature.prompts.PromptMiddleware
import mozilla.components.feature.prompts.file.FileUploadsDirCleaner
import mozilla.components.feature.prompts.file.FileUploadsDirCleanerMiddleware
import mozilla.components.feature.pwa.ManifestStorage
import mozilla.components.feature.pwa.WebAppShortcutManager
import mozilla.components.feature.readerview.ReaderViewMiddleware
import mozilla.components.feature.recentlyclosed.RecentlyClosedMiddleware
import mozilla.components.feature.recentlyclosed.RecentlyClosedTabsStorage
import mozilla.components.feature.search.ext.createApplicationSearchEngine
import mozilla.components.feature.search.middleware.SearchMiddleware
import mozilla.components.feature.search.region.RegionMiddleware
import mozilla.components.feature.session.HistoryDelegate
import mozilla.components.feature.session.middleware.LastAccessMiddleware
import mozilla.components.feature.session.middleware.undo.UndoMiddleware
import mozilla.components.feature.sitepermissions.OnDiskSitePermissionsStorage
import mozilla.components.feature.top.sites.DefaultTopSitesStorage
import mozilla.components.feature.top.sites.PinnedSiteStorage
import mozilla.components.feature.webcompat.WebCompatFeature
import mozilla.components.feature.webcompat.reporter.WebCompatReporterFeature
import mozilla.components.feature.webnotifications.WebNotificationFeature
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.mars.contile.ContileTopSitesProvider
import mozilla.components.service.mars.contile.ContileTopSitesUpdater
import mozilla.components.service.digitalassetlinks.RelationChecker
import mozilla.components.service.digitalassetlinks.local.StatementApi
import mozilla.components.service.digitalassetlinks.local.StatementRelationChecker
import mozilla.components.service.location.LocationService
import mozilla.components.service.location.MozillaLocationService
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import mozilla.components.support.base.worker.Frequency
import org.midorinext.android.*
import org.midorinext.android.components.search.SearchMigration
import org.midorinext.android.downloads.DownloadService
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings
import org.midorinext.android.gecko.GeckoProvider
import org.midorinext.android.historymetadata.DefaultHistoryMetadataService
import org.midorinext.android.historymetadata.HistoryMetadataMiddleware
import org.midorinext.android.historymetadata.HistoryMetadataService
import org.midorinext.android.media.MediaSessionService
import org.midorinext.android.perf.StrictModeManager
import org.midorinext.android.perf.lazyMonitored
import org.midorinext.android.settings.SupportUtils
import org.midorinext.android.utils.getUndoDelay
import org.mozilla.geckoview.GeckoRuntime
import java.util.concurrent.TimeUnit

/**
 * Component group for all core browser functionality.
 */
@Suppress("LargeClass")
class Core(
    private val context: Context,
    private val crashReporter: CrashReporting,
    strictMode: StrictModeManager
) {
    /**
     * The browser engine component initialized based on the build
     * configuration (see build variants).
     */
    val engine: Engine by lazyMonitored {
        val defaultSettings = DefaultSettings(
            requestInterceptor = requestInterceptor,
            remoteDebuggingEnabled = context.settings().isRemoteDebuggingEnabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M,
            testingModeEnabled = false,
            trackingProtectionPolicy = trackingProtectionPolicyFactory.createTrackingProtectionPolicy(),
            historyTrackingDelegate = HistoryDelegate(lazyHistoryStorage),
            preferredColorScheme = getPreferredColorScheme(),
            automaticFontSizeAdjustment = context.settings().shouldUseAutoSize,
            fontInflationEnabled = context.settings().shouldUseAutoSize,
            suspendMediaWhenInactive = false,
            forceUserScalableContent = context.settings().forceEnableZoom,
            loginAutofillEnabled = context.settings().shouldAutofillLogins,
            enterpriseRootsEnabled = context.settings().allowThirdPartyRootCerts,
            clearColor = ContextCompat.getColor(
                context,
                R.color.fx_mobile_layer_color_1,
            ),
            httpsOnlyMode = context.settings().getHttpsOnlyMode(),
        )

        GeckoEngine(
            context,
            defaultSettings,
            geckoRuntime,
        ).also {
            WebCompatFeature.install(it)

            /**
             * There are some issues around localization to be resolved, as well as questions around
             * the capacity of the WebCompat team, so the "Report site issue" feature should stay
             * disabled in Midori Release builds for now.
             * This is consistent with both Fennec and Midori Desktop.
             */
            if (Config.channel.isDebug) {
                WebCompatReporterFeature.install(it, "midori")
            }
        }
    }

    /**
     * Passed to [engine] to intercept requests for app links,
     * and various features triggered by page load requests.
     *
     * NB: This does not need to be lazy as it is initialized
     * with the engine on startup.
     */
    val requestInterceptor = AppRequestInterceptor(context)

    /**
     * [Client] implementation to be used for code depending on `concept-fetch``
     */
    val client: Client by lazyMonitored {
        GeckoViewFetchClient(
            context,
            geckoRuntime,
        )
    }

    val fileUploadsDirCleaner: FileUploadsDirCleaner by lazyMonitored {
        FileUploadsDirCleaner { context.cacheDir }
    }

    val geckoRuntime: GeckoRuntime by lazyMonitored {
        GeckoProvider.getOrCreateRuntime(
            context,
            lazyAutofillStorage,
            lazyPasswordsStorage,
            trackingProtectionPolicyFactory.createTrackingProtectionPolicy(),
        )
    }

    val geckoSitePermissionsStorage by lazyMonitored {
        GeckoSitePermissionsStorage(geckoRuntime, OnDiskSitePermissionsStorage(context))
    }

    val sessionStorage: SessionStorage by lazyMonitored {
        SessionStorage(context, engine = engine)
    }

    private val locationService: LocationService by lazyMonitored {
        if (Config.channel.isDebug || BuildConfig.MLS_TOKEN.isEmpty()) {
            LocationService.default()
        } else {
            MozillaLocationService(context, client, BuildConfig.MLS_TOKEN)
        }
    }

    val applicationSearchEngines: List<SearchEngine> by lazyMonitored {
        listOf(
            createApplicationSearchEngine(
                id = BOOKMARKS_SEARCH_ENGINE_ID,
                name = context.getString(R.string.library_bookmarks),
                url = "",
                icon = getDrawable(context, R.drawable.ic_bookmarks_search)?.toBitmap()!!,
            ),
            createApplicationSearchEngine(
                id = TABS_SEARCH_ENGINE_ID,
                name = context.getString(R.string.preferences_tabs),
                url = "",
                icon = getDrawable(context, R.drawable.ic_tabs_search)?.toBitmap()!!,
            ),
            createApplicationSearchEngine(
                id = HISTORY_SEARCH_ENGINE_ID,
                name = context.getString(R.string.library_history),
                url = "",
                icon = getDrawable(context, R.drawable.ic_history_search)?.toBitmap()!!,
            ),
        )
    }

    /**
     * The [BrowserStore] holds the global [BrowserState].
     */
    val store by lazyMonitored {
        val middlewareList =
            mutableListOf(
                LastAccessMiddleware(),
                RecentlyClosedMiddleware(recentlyClosedTabsStorage, RECENTLY_CLOSED_MAX),
                DownloadMiddleware(context, DownloadService::class.java),
                ReaderViewMiddleware(),
                ThumbnailsMiddleware(thumbnailStorage),
                UndoMiddleware(context.getUndoDelay()),
                RegionMiddleware(context, locationService),
                SearchMiddleware(
                    context,
                    additionalBundledSearchEngineIds = listOf("startpage"),
                    migration = SearchMigration(context),
                ),
                RecordingDevicesMiddleware(context, context.components.notificationsDelegate),
                PromptMiddleware(),
                LastMediaAccessMiddleware(),
                HistoryMetadataMiddleware(historyMetadataService),
                SessionPrioritizationMiddleware(),
                FileUploadsDirCleanerMiddleware(fileUploadsDirCleaner),
            )

        BrowserStore(
            initialState = BrowserState(
                search = SearchState(
                    applicationSearchEngines = if (context.settings().showUnifiedSearchFeature) {
                        applicationSearchEngines
                    } else {
                        emptyList()
                    },
                ),
            ),
            middleware = middlewareList + EngineMiddleware.create(
                engine,
                // We are disabling automatic suspending of engine sessions under memory pressure.
                // Instead we solely rely on GeckoView and the Android system to reclaim memory
                // when needed. For details, see:
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1752594
                // https://github.com/mozilla-mobile/fenix/issues/12731
                // https://github.com/mozilla-mobile/android-components/issues/11300
                // https://github.com/mozilla-mobile/android-components/issues/11653
                trimMemoryAutomatically = false,
            ),
        ).apply {
            // Install the "icons" WebExtension to automatically load icons for every visited website.
            icons.install(engine, this)

            WebNotificationFeature(
                context,
                engine,
                icons,
                R.drawable.ic_status_logo,
                permissionStorage.permissionsStorage,
                IntentReceiverActivity::class.java,
                notificationsDelegate = context.components.notificationsDelegate
            )

            MediaSessionFeature(context, MediaSessionService::class.java, this).start()
        }
    }

    /**
     * The [CustomTabsServiceStore] holds global custom tabs related data.
     */
    val customTabsStore by lazyMonitored { CustomTabsServiceStore() }

    /**
     * The [RelationChecker] checks Digital Asset Links relationships for Trusted Web Activities.
     */
    val relationChecker: RelationChecker by lazyMonitored {
        StatementRelationChecker(StatementApi(client))
    }

    /**
     * The [HistoryMetadataService] is used to record history metadata.
     */
    val historyMetadataService: HistoryMetadataService by lazyMonitored {
        DefaultHistoryMetadataService(storage = historyStorage)
    }

    /**
     * Icons component for loading, caching and processing website icons.
     */
    val icons by lazyMonitored {
        BrowserIcons(context, client)
    }

    /**
     * Shortcut component for managing shortcuts on the device home screen.
     */
    val webAppShortcutManager by lazyMonitored {
        WebAppShortcutManager(
            context,
            client,
            webAppManifestStorage,
        )
    }

    // Lazy wrappers around storage components are used to pass references to these components without
    // initializing them until they're accessed.
    // Use these for startup-path code, where we don't want to do any work that's not strictly necessary.
    // For example, this is how the GeckoEngine delegates (history, logins) are configured.
    // We can fully initialize GeckoEngine without initialized our storage.
    val lazyHistoryStorage = lazyMonitored { PlacesHistoryStorage(context, crashReporter) }
    val lazyBookmarksStorage = lazyMonitored { PlacesBookmarksStorage(context) }
    val lazyPasswordsStorage = lazyMonitored { SyncableLoginsStorage(context, lazySecurePrefs) }
    val lazyAutofillStorage =
        lazyMonitored { AutofillCreditCardsAddressesStorage(context, lazySecurePrefs) }
    val lazyDomainsAutocompleteProvider = lazyMonitored {
        // Assume this is used together with other autocomplete providers (like history) which have priority 0
        // and set priority 1 for the domains provider to ensure other providers' results are shown first.
        ShippedDomainsProvider(1).also { shippedDomainsProvider ->
            shippedDomainsProvider.initialize(context)
        }
    }
    val lazySessionAutocompleteProvider = lazyMonitored {
        SessionAutocompleteProvider(store)
    }

    /**
     * The storage component to sync and persist tabs in a Midori Sync account.
     */
    val lazyRemoteTabsStorage = lazyMonitored { RemoteTabsStorage(context, crashReporter) }

    val recentlyClosedTabsStorage =
        lazyMonitored { RecentlyClosedTabsStorage(context, engine, crashReporter) }

    // For most other application code (non-startup), these wrappers are perfectly fine and more ergonomic.
    val historyStorage: PlacesHistoryStorage get() = lazyHistoryStorage.value
    val bookmarksStorage: PlacesBookmarksStorage get() = lazyBookmarksStorage.value
    val passwordsStorage: SyncableLoginsStorage get() = lazyPasswordsStorage.value
    val autofillStorage: AutofillCreditCardsAddressesStorage get() = lazyAutofillStorage.value
    val domainsAutocompleteProvider: BaseDomainAutocompleteProvider get() = lazyDomainsAutocompleteProvider.value
    val sessionAutocompleteProvider: SessionAutocompleteProvider get() = lazySessionAutocompleteProvider.value

    val tabCollectionStorage by lazyMonitored {
        TabCollectionStorage(
            context,
            strictMode,
        )
    }

    /**
     * A storage component for persisting thumbnail images of tabs.
     */
    val thumbnailStorage by lazyMonitored { ThumbnailStorage(context) }

    val pinnedSiteStorage by lazyMonitored { PinnedSiteStorage(context) }

    val contileTopSitesProvider by lazyMonitored {
        ContileTopSitesProvider(
            context = context,
            client = client,
            maxCacheAgeInSeconds = CONTILE_MAX_CACHE_AGE,
        )
    }

    @Suppress("MagicNumber")
    val contileTopSitesUpdater by lazyMonitored {
        ContileTopSitesUpdater(
            context = context,
            provider = contileTopSitesProvider,
            frequency = Frequency(3, TimeUnit.HOURS),
        )
    }

    val topSitesStorage by lazyMonitored {
        val defaultTopSites = mutableListOf<Pair<String, String>>()

        strictMode.resetAfter(StrictMode.allowThreadDiskReads()) {
            if (!context.settings().defaultTopSitesAdded) {
                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_expedia),
                        SupportUtils.EXPEDIA_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_ebay),
                        SupportUtils.EBAY_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_amazon),
                        SupportUtils.AMAZON_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_aliexpress),
                        SupportUtils.ALIEXPRESS_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_stakeus),
                        SupportUtils.STAKEUS_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_stakecom),
                        SupportUtils.STAKECOM_URL,
                    )
                )

                defaultTopSites.add(
                    Pair(
                        context.getString(R.string.default_top_site_adamant),
                        SupportUtils.ADAMANT_URL,
                    )
                )

                context.settings().defaultTopSitesAdded = true
            }
        }

        DefaultTopSitesStorage(
            pinnedSitesStorage = pinnedSiteStorage,
            historyStorage = historyStorage,
            topSitesProvider = contileTopSitesProvider,
            defaultTopSites = defaultTopSites,
        )
    }

    val permissionStorage by lazyMonitored { PermissionStorage(context) }

    val webAppManifestStorage by lazyMonitored { ManifestStorage(context) }

    val loginExceptionStorage by lazyMonitored { LoginExceptionStorage(context) }

    /**
     * Shared Preferences that encrypt/decrypt using Android KeyStore and lib-dataprotect for 23+
     * only on Debug for now, otherwise simply stored.
     * See https://github.com/mozilla-mobile/fenix/issues/8324
     * Also, this needs revision. See https://github.com/mozilla-mobile/fenix/issues/19155
     */
    private fun getSecureAbove22Preferences() =
        SecureAbove22Preferences(
            context = context,
            name = KEY_STORAGE_NAME,
            forceInsecure = !Config.channel.isDebug,
        )

    // Temporary. See https://github.com/mozilla-mobile/fenix/issues/19155
    private val lazySecurePrefs = lazyMonitored { getSecureAbove22Preferences() }
    val trackingProtectionPolicyFactory =
        TrackingProtectionPolicyFactory(context.settings(), context.resources)

    /**
     * Sets Preferred Color scheme based on Dark/Light Theme Settings or Current Configuration
     */
    fun getPreferredColorScheme(): PreferredColorScheme {
        val inDark =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        return when {
            context.settings().shouldUseDarkTheme -> PreferredColorScheme.Dark
            context.settings().shouldUseLightTheme -> PreferredColorScheme.Light
            inDark -> PreferredColorScheme.Dark
            else -> PreferredColorScheme.Light
        }
    }

    companion object {
        private const val KEY_STORAGE_NAME = "core_prefs"
        private const val RECENTLY_CLOSED_MAX = 10
        const val HISTORY_METADATA_MAX_AGE_IN_MS = 14 * 24 * 60 * 60 * 1000 // 14 days
        private const val CONTILE_MAX_CACHE_AGE = 60L // 60 minutes
        const val HISTORY_SEARCH_ENGINE_ID = "history_search_engine_id"
        const val BOOKMARKS_SEARCH_ENGINE_ID = "bookmarks_search_engine_id"
        const val TABS_SEARCH_ENGINE_ID = "tabs_search_engine_id"

        // Maximum number of suggestions returned from the history search engine source.
        const val METADATA_HISTORY_SUGGESTION_LIMIT = 100

        // Maximum number of suggestions returned from shortcut search engine.
        const val METADATA_SHORTCUT_SUGGESTION_LIMIT = 20
    }
}
