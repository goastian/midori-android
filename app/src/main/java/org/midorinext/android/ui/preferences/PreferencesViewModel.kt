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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mozilla.components.browser.engine.gecko.permission.GeckoSitePermissionsStorage
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.concept.engine.permission.SitePermissionsStorage
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

@HiltViewModel
class PreferencesViewModel @Inject constructor(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val appTrackingProtectionController: AppTrackingProtectionController,
    private val permissionsStorage: GeckoSitePermissionsStorage,
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