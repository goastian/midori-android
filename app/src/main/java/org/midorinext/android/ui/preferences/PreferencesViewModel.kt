package org.midorinext.android.ui.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import org.midorinext.android.apptracking.AppTrackingProtectionController
import org.midorinext.android.apptracking.AppTrackingRuntimeState
import org.midorinext.android.preferences.app.*
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import mozilla.components.browser.state.action.TranslationsAction
import mozilla.components.browser.state.state.TranslationsBrowserState
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.translate.LanguageSetting
import mozilla.components.concept.engine.translate.ModelManagementOptions
import mozilla.components.concept.engine.translate.ModelOperation
import mozilla.components.concept.engine.translate.OperationLevel
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val appTrackingProtectionController: AppTrackingProtectionController,
    private val permissionsStorage: GeckoSitePermissionsStorage,
    private val store: BrowserStore,
    tabsUseCases: TabsUseCases,
    MidoriUseCases: MidoriUseCases
) : ViewModel() {
    val appPreferences = appPreferencesRepository.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppPreferences.getDefaultInstance()
    )

    val clearDataPreferences = appPreferencesRepository.clearDataPreferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ClearDataPreferences(
            browsingData = Engine.BrowsingData.select(Engine.BrowsingData.ALL),
            tabs = true,
            history = true
        )
    )

    var permissions: MutableStateFlow<PagingData<SitePermissions>> = MutableStateFlow(PagingData.empty())
        private set
    private var permissionsCollectionJob: Job? = null

    val systemProtectionRunning = appTrackingProtectionController.systemProtectionRunning
    val appTrackingMetrics = AppTrackingRuntimeState.metrics

    val translationState = store.flow().map { it.translationEngine }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TranslationsBrowserState()
    )

    fun ensurePermissionsLoaded() {
        if (permissionsCollectionJob != null) return

        permissionsCollectionJob = viewModelScope.launch(Dispatchers.IO) {
            permissionsStorage.getSitePermissionsPaged().asPagingSourceFactory().let { source ->
                Pager<Int, SitePermissions>(PagingConfig(pageSize = 50)) { source.invoke() }
                    .flow
                    .distinctUntilChanged()
                    .cachedIn(viewModelScope)
                    .collect {
                        permissions.value = it
                    }
            }
        }
    }

    fun updateToolbarPosition(position: ToolbarPosition) {
        viewModelScope.launch { appPreferencesRepository.updateToolbarPosition(position) }
    }

    fun updateHideToolbarOnScroll(hideOnScroll: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateHideToolbarOnScroll(hideOnScroll) }
    }

    fun updateTabsView(option: TabsViewOption) {
        viewModelScope.launch { appPreferencesRepository.updateTabsView(option) }
    }

    fun updateOpenLinksInApp(openInApp: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateOpenLinksInApp(openInApp) }
    }

    fun updateShowNewTabHome(show: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateShowNewTabHome(show) }
    }

    fun updateHomepageOpeningScreen(screen: HomepageOpeningScreen) {
        viewModelScope.launch { appPreferencesRepository.updateHomepageOpeningScreen(screen) }
    }

    fun updatePullToRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updatePullToRefreshEnabled(enabled) }
    }

    fun updateDownloadWifiOnly(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateDownloadWifiOnly(enabled) }
    }

    fun updateToolbarShortcut(shortcut: ToolbarShortcut) {
        viewModelScope.launch { appPreferencesRepository.updateToolbarShortcut(shortcut) }
    }

    fun updateSwipeAddressBarToSwitchTabsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateSwipeAddressBarToSwitchTabsEnabled(enabled)
        }
    }

    fun updateSwipeToolbarToShowTabsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateSwipeToolbarToShowTabsEnabled(enabled)
        }
    }

    fun updateShakeToSummarizeEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateShakeToSummarizeEnabled(enabled) }
    }

    fun updateDownloadRemovalBehavior(behavior: DownloadRemovalBehavior) {
        viewModelScope.launch { appPreferencesRepository.updateDownloadRemovalBehavior(behavior) }
    }

    fun updateManageDownloadsWithOtherApp(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateManageDownloadsWithOtherApp(enabled) }
    }

    fun updateTranslationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateTranslationsEnabled(enabled)
            store.dispatch(TranslationsAction.SetTranslationsEnabledAction(enabled))
        }
    }

    fun updateTranslationsDownloadInDataSaver(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateTranslationsDownloadInDataSaver(enabled)
        }
    }

    fun updateTranslationOffer(enabled: Boolean) {
        store.dispatch(TranslationsAction.UpdateGlobalOfferTranslateSettingAction(enabled))
    }

    fun updateAutomaticTranslation(languageCode: String, enabled: Boolean) {
        store.dispatch(
            TranslationsAction.UpdateLanguageSettingsAction(
                languageCode = languageCode,
                setting = if (enabled) LanguageSetting.ALWAYS else LanguageSetting.OFFER
            )
        )
    }

    fun removeNeverTranslateSite(origin: String) {
        store.dispatch(TranslationsAction.RemoveNeverTranslateSiteAction(origin))
    }

    fun manageLanguageModel(languageCode: String, download: Boolean) {
        store.dispatch(
            TranslationsAction.ManageLanguageModelsAction(
                ModelManagementOptions(
                    languageToManage = languageCode,
                    operation = if (download) ModelOperation.DOWNLOAD else ModelOperation.DELETE,
                    operationLevel = OperationLevel.LANGUAGE
                )
            )
        )
    }

    fun updateSavePasswordsEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateSavePasswordsEnabled(enabled) }
    }

    fun updatePasswordAutofillEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updatePasswordAutofillEnabled(enabled) }
    }

    fun updateAutofillAddressesEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAutofillAddressesEnabled(enabled) }
    }

    fun updateAutofillCardsEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAutofillCardsEnabled(enabled) }
    }

    fun updateAccessibilityAutomaticFontSizing(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAccessibilityAutomaticFontSizing(enabled) }
    }

    fun updateAccessibilityFontScale(scale: Int) {
        viewModelScope.launch { appPreferencesRepository.updateAccessibilityFontScale(scale) }
    }

    fun updateAccessibilityForceZoomEnabled(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateAccessibilityForceZoomEnabled(enabled) }
    }

    fun updateClearDataOnQuit(clear: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateClearDataOnQuit(clear) }
    }

    fun updateAppearance(appearance: Appearance) { // TODO move appearance to app prefs
        viewModelScope.launch { appPreferencesRepository.updateAppearance(appearance) }
    }

    fun updateClearDataPreferences(preferences: ClearDataPreferences) {
        viewModelScope.launch { appPreferencesRepository.updateClearDataPreferences(preferences) }
    }

    fun updateGlobalPrivacyControl(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateGlobalPrivacyControl(enabled) }
    }

    fun updateFingerprintingProtection(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateFingerprintingProtection(enabled) }
    }

    fun updateCookiePartitioning(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateCookiePartitioning(enabled) }
    }

    fun updateStrictTrackingProtection(enabled: Boolean) {
        viewModelScope.launch { appPreferencesRepository.updateStrictTrackingProtection(enabled) }
    }

    fun updateTrackingProtectionLevel(level: TrackingProtectionLevel) {
        viewModelScope.launch { appPreferencesRepository.updateTrackingProtectionLevel(level) }
    }

    fun updateDohProvider(provider: DoHProvider) {
        viewModelScope.launch { appPreferencesRepository.updateDohProvider(provider) }
    }

    fun updateHttpsOnlyLevel(level: HttpsOnlyLevel) {
        viewModelScope.launch { appPreferencesRepository.updateHttpsOnlyLevel(level) }
    }

    fun updateAppTrackingProtectionMode(mode: AppTrackingProtectionMode) {
        viewModelScope.launch { appPreferencesRepository.updateAppTrackingProtectionMode(mode) }
    }

    fun updateAppTrackingSystemEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesRepository.updateAppTrackingSystemEnabled(enabled)
        }
    }

    fun addAppTrackingExcludedPackage(packageName: String) {
        viewModelScope.launch {
            appPreferencesRepository.addAppTrackingExcludedPackage(packageName)
        }
    }

    fun removeAppTrackingExcludedPackage(packageName: String) {
        viewModelScope.launch {
            appPreferencesRepository.removeAppTrackingExcludedPackage(packageName)
        }
    }

    fun clearAppTrackingExcludedPackages() {
        viewModelScope.launch {
            appPreferencesRepository.clearAppTrackingExcludedPackages()
        }
    }

    fun getSystemProtectionPermissionIntent() = appTrackingProtectionController.getVpnPermissionIntent()

    fun startSystemProtection() {
        viewModelScope.launch {
            appTrackingProtectionController.startSystemProtection()
        }
    }

    fun stopSystemProtection() {
        viewModelScope.launch {
            appTrackingProtectionController.stopSystemProtection()
        }
    }

    fun updatePermissions(permissions: SitePermissions, permission: SitePermissionsStorage.Permission, value: SitePermissions.Status) {
        viewModelScope.launch(Dispatchers.IO) {
            val newPermission = getNewPermission(permissions, permission, value)
            permissionsStorage.update(newPermission, true)
            permissionsStorage.update(newPermission, false)
        }
    }

    private fun getNewPermission(permissions: SitePermissions, permission: SitePermissionsStorage.Permission, value: SitePermissions.Status): SitePermissions {
        val autoplayStatus = when (value) {
            SitePermissions.Status.BLOCKED -> SitePermissions.AutoplayStatus.BLOCKED
            SitePermissions.Status.NO_DECISION -> SitePermissions.AutoplayStatus.BLOCKED
            SitePermissions.Status.ALLOWED -> SitePermissions.AutoplayStatus.ALLOWED
        }
        return when (permission) {
            SitePermissionsStorage.Permission.MICROPHONE -> permissions.copy(microphone = value)
            SitePermissionsStorage.Permission.BLUETOOTH -> permissions.copy(bluetooth = value)
            SitePermissionsStorage.Permission.CAMERA -> permissions.copy(camera = value)
            SitePermissionsStorage.Permission.LOCAL_STORAGE -> permissions.copy(localStorage = value)
            SitePermissionsStorage.Permission.NOTIFICATION -> permissions.copy(notification = value)
            SitePermissionsStorage.Permission.LOCATION -> permissions.copy(location = value)
            SitePermissionsStorage.Permission.AUTOPLAY_AUDIBLE -> permissions.copy(autoplayAudible = autoplayStatus)
            SitePermissionsStorage.Permission.AUTOPLAY_INAUDIBLE -> permissions.copy(autoplayInaudible = autoplayStatus)
            SitePermissionsStorage.Permission.MEDIA_KEY_SYSTEM_ACCESS -> permissions.copy(mediaKeySystemAccess = value)
            SitePermissionsStorage.Permission.STORAGE_ACCESS -> permissions.copy(crossOriginStorageAccess = value)
            SitePermissionsStorage.Permission.LOCAL_DEVICE_ACCESS -> permissions.copy(localDeviceAccess = value)
            SitePermissionsStorage.Permission.LOCAL_NETWORK_ACCESS -> permissions.copy(localNetworkAccess = value)
        }
    }

    fun revokeAllPermissions(permissions: SitePermissions? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            if (permissions != null) {
                permissionsStorage.remove(permissions, true)
                permissionsStorage.remove(permissions, false)
            } else {
                permissionsStorage.removeAll()
            }
        }
    }
    val addTabsUseCase = tabsUseCases.addTab
    val openTestTabUseCase = MidoriUseCases.openTestPageUseCase
}
