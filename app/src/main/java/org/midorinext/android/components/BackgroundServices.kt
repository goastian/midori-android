/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.annotation.VisibleForTesting.Companion.PRIVATE
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.browser.storage.sync.PlacesBookmarksStorage
import mozilla.components.browser.storage.sync.PlacesHistoryStorage
import mozilla.components.browser.storage.sync.RemoteTabsStorage
import mozilla.components.concept.sync.*
import mozilla.components.feature.accounts.push.FxaPushSupportFeature
import mozilla.components.feature.accounts.push.SendTabFeature
import mozilla.components.feature.syncedtabs.SyncedTabsAutocompleteProvider
import mozilla.components.feature.syncedtabs.storage.SyncedTabsStorage
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.service.fxa.PeriodicSyncConfig
import mozilla.components.service.fxa.ServerConfig
import mozilla.components.service.fxa.SyncConfig
import mozilla.components.service.fxa.SyncEngine
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.SCOPE_SESSION
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import mozilla.components.service.fxa.store.SyncStore
import mozilla.components.service.fxa.store.SyncStoreSupport
import mozilla.components.service.fxa.sync.GlobalSyncableStoreProvider
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import mozilla.components.support.utils.RunWhenReadyQueue
import org.midorinext.android.Config
import org.midorinext.android.R
import org.midorinext.android.ext.components
import org.midorinext.android.perf.StrictModeManager
import org.midorinext.android.perf.lazyMonitored
import org.midorinext.android.sync.SyncedTabsIntegration

/**
 * Component group for background services. These are the components that need to be accessed from within a
 * background worker.
 */
@Suppress("LongParameterList")
class BackgroundServices(
    private val context: Context,
    private val push: Push,
    crashReporter: CrashReporter,
    historyStorage: Lazy<PlacesHistoryStorage>,
    bookmarkStorage: Lazy<PlacesBookmarksStorage>,
    passwordsStorage: Lazy<SyncableLoginsStorage>,
    remoteTabsStorage: Lazy<RemoteTabsStorage>,
    creditCardsStorage: Lazy<AutofillCreditCardsAddressesStorage>,
    strictMode: StrictModeManager
) {
    // Allows executing tasks which depend on the account manager, but do not need to eagerly initialize it.
    val accountManagerAvailableQueue = RunWhenReadyQueue()

    fun defaultDeviceName(context: Context): String =
        context.getString(
            R.string.default_device_name_2,
            context.getString(R.string.app_name),
            Build.MANUFACTURER,
            Build.MODEL
        )

    val serverConfig = FxaServer.config(context)
    private val deviceConfig = DeviceConfig(
        name = defaultDeviceName(context),
        type = DeviceType.MOBILE,

        // NB: flipping this flag back and worth is currently not well supported and may need hand-holding.
        // Consult with the android-components peers before changing.
        // See https://github.com/mozilla/application-services/issues/1308
        capabilities = setOf(DeviceCapability.SEND_TAB),

        // Enable encryption for account state on supported API levels (23+).
        // Just on local builds for now.
        // Enabling this for all channels is tracked in https://github.com/mozilla-mobile/fenix/issues/6704
        secureStateAtRest = Config.channel.isDebug
    )

    @VisibleForTesting
    val supportedEngines =
        setOfNotNull(
            SyncEngine.History,
            SyncEngine.Bookmarks,
            SyncEngine.Passwords,
            SyncEngine.Tabs,
            SyncEngine.CreditCards,
            SyncEngine.Addresses
        )
    private val syncConfig =
        SyncConfig(supportedEngines, PeriodicSyncConfig(periodMinutes = 240)) // four hours

    private val creditCardKeyProvider by lazyMonitored { creditCardsStorage.value.crypto }
    private val passwordKeyProvider by lazyMonitored { passwordsStorage.value.crypto }

    init {
        // Make the "history", "bookmark", "passwords", "tabs", "credit cards" stores
        // accessible to workers spawned by the sync manager.
        GlobalSyncableStoreProvider.configureStore(SyncEngine.History to historyStorage)
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Bookmarks to bookmarkStorage)
        GlobalSyncableStoreProvider.configureStore(
            storePair = SyncEngine.Passwords to passwordsStorage,
            keyProvider = lazy { passwordKeyProvider }
        )
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Tabs to remoteTabsStorage)
        GlobalSyncableStoreProvider.configureStore(
            storePair = SyncEngine.CreditCards to creditCardsStorage,
            keyProvider = lazy { creditCardKeyProvider }
        )
        GlobalSyncableStoreProvider.configureStore(SyncEngine.Addresses to creditCardsStorage)
    }

    val accountAbnormalities = AccountAbnormalities(context, crashReporter, strictMode)

    val syncStore by lazyMonitored {
        SyncStore()
    }

    private lateinit var syncStoreSupport: SyncStoreSupport

    val accountManager by lazyMonitored {
        makeAccountManager(context, serverConfig, deviceConfig, syncConfig, crashReporter)
    }

    val syncedTabsStorage by lazyMonitored {
        SyncedTabsStorage(accountManager, context.components.core.store, remoteTabsStorage.value)
    }
    val syncedTabsAutocompleteProvider by lazyMonitored {
        SyncedTabsAutocompleteProvider(syncedTabsStorage)
    }

    @VisibleForTesting(otherwise = PRIVATE)
    fun makeAccountManager(
        context: Context,
        serverConfig: ServerConfig,
        deviceConfig: DeviceConfig,
        syncConfig: SyncConfig?,
        crashReporter: CrashReporter?
    ) = FxaAccountManager(
        context,
        serverConfig,
        deviceConfig,
        syncConfig,
        setOf(
            // We don't need to specify sync scope explicitly, but `syncConfig` may be disabled due to
            // an 'experiments' flag. In that case, sync scope necessary for syncing won't be acquired
            // during authentication unless we explicitly specify it below.
            // This is a good example of an information leak at the API level.
            // See https://github.com/mozilla-mobile/android-components/issues/3732
            SCOPE_SYNC,
            // Necessary to enable "Manage Account" functionality and ability to generate OAuth
            // codes for certain scopes.
            SCOPE_SESSION
        ),
        crashReporter
    ).also { accountManager ->
        // Register an "abnormal fxa behaviour" middleware to keep track of events such as
        // unexpected logouts.
        accountManager.register(accountAbnormalities)

        accountManager.register(AccountManagerReadyObserver(accountManagerAvailableQueue))

        // Enable push if it's configured.
        push.feature?.let { autoPushFeature ->
            FxaPushSupportFeature(context, accountManager, autoPushFeature, crashReporter)
        }

        SendTabFeature(accountManager) { device, tabs ->
            notificationManager.showReceivedTabs(context, device, tabs)
        }

        SyncedTabsIntegration(context, accountManager).launch()

        syncStoreSupport = SyncStoreSupport(syncStore, lazyOf(accountManager)).also {
            it.initialize()
        }

        MainScope().launch {
            accountManager.start()
        }
    }

    /**
     * Provides notification functionality, manages notification channels.
     */
    private val notificationManager by lazyMonitored {
        NotificationManager(context)
    }
}

private class AccountManagerReadyObserver(
    private val accountManagerAvailableQueue: RunWhenReadyQueue
) : AccountObserver {
    override fun onReady(authenticatedAccount: OAuthAccount?) {
        accountManagerAvailableQueue.ready()
    }
}
