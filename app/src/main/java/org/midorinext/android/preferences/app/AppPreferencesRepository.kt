package org.midorinext.android.preferences.app

import android.util.Log
import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.*
import mozilla.components.concept.engine.Engine
import java.io.IOException
import javax.inject.Inject


private const val LOGTAG: String = "AppPreferencesRepo"

data class ClearDataPreferences(
    val browsingData: Engine.BrowsingData,
    val tabs: Boolean,
    val history: Boolean
)

// Not needed, while this class remains stateless
// @Module
// @InstallIn(ActivityRetainedComponent::class)
class AppPreferencesRepository @Inject constructor(
    private val datastore: DataStore<AppPreferences>
) {
    val flow: Flow<AppPreferences> = datastore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(LOGTAG, "Error reading frontend preferences.", exception)
                emit(AppPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    val clearDataPreferencesFlow = flow
        .map { ClearDataPreferences(
            browsingData = Engine.BrowsingData.select(it.clearDataBrowsingdata),
            tabs = it.clearDataTabs,
            history = it.clearDataHistory
        )}

    val tabGroupColorsFlow: Flow<Map<String, Int>> = flow
        .map { it.tabGroupColorsMap }

    suspend fun updateToolbarPosition(position: ToolbarPosition) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setToolbarPosition(position).build()
        }
    }

    suspend fun updateHideToolbarOnScroll(hideOnScroll: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHideToolbarOnScroll(hideOnScroll).build()
        }
    }

    suspend fun updateTabsView(option: TabsViewOption) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setTabsView(option).build()
        }
    }

    suspend fun updateTabGroupColor(groupId: String, color: Int) {
        datastore.updateData { preferences ->
            preferences.toBuilder()
                .putTabGroupColors(groupId, color)
                .build()
        }
    }

    suspend fun removeTabGroupColor(groupId: String) {
        datastore.updateData { preferences ->
            preferences.toBuilder()
                .removeTabGroupColors(groupId)
                .build()
        }
    }

    suspend fun updateOpenLinksInApp(openInApp: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setOpenLinksInApp(openInApp).build()
        }
    }

    suspend fun updateDownloadWifiOnly(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setDownloadWifiOnly(enabled).build()
        }
    }

    suspend fun updateShowNewTabHome(show: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setOpenBlankNewTab(!show).build()
        }
    }

    suspend fun updateHomepageOpeningScreen(screen: HomepageOpeningScreen) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHomepageOpeningScreen(screen).build()
        }
    }

    suspend fun updatePullToRefreshEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setPullToRefreshEnabled(enabled).build()
        }
    }

    suspend fun updateToolbarShortcut(shortcut: ToolbarShortcut) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setToolbarShortcut(shortcut).build()
        }
    }

    suspend fun updateSwipeAddressBarToSwitchTabsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setSwipeAddressBarToSwitchTabsEnabled(enabled).build()
        }
    }

    suspend fun updateSwipeToolbarToShowTabsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setSwipeToolbarToShowTabsEnabled(enabled).build()
        }
    }

    suspend fun updateShakeToSummarizeEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setShakeToSummarizeEnabled(enabled).build()
        }
    }

    suspend fun updateDownloadRemovalBehavior(behavior: DownloadRemovalBehavior) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setDownloadRemovalBehavior(behavior).build()
        }
    }

    suspend fun updateManageDownloadsWithOtherApp(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setManageDownloadsWithOtherApp(enabled).build()
        }
    }

    suspend fun updateTranslationsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setTranslationsDisabled(!enabled).build()
        }
    }

    suspend fun updateTranslationsDownloadInDataSaver(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setTranslationsDownloadInDataSaver(enabled).build()
        }
    }

    suspend fun updateSavePasswordsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setSavePasswordsEnabled(enabled).build()
        }
    }

    suspend fun updatePasswordAutofillEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setPasswordAutofillEnabled(enabled).build()
        }
    }

    suspend fun updateAutofillAddressesEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setAutofillAddressesEnabled(enabled).build()
        }
    }

    suspend fun updateAutofillCardsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setAutofillCardsEnabled(enabled).build()
        }
    }

    suspend fun updateAccessibilityAutomaticFontSizing(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setAccessibilityAutomaticFontSizing(enabled).build()
        }
    }

    suspend fun updateAccessibilityFontScale(scale: Int) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setAccessibilityFontScale(scale.coerceIn(80, 150)).build()
        }
    }

    suspend fun updateAccessibilityForceZoomEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setAccessibilityForceZoomEnabled(enabled).build()
        }
    }

    suspend fun updateClearDataOnQuit(clear: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setClearDataOnQuit(clear).build()
        }
    }

    suspend fun updateClearDataPreferences(preferences: ClearDataPreferences) {
        datastore.updateData { prefs ->
            prefs.toBuilder()
                .setClearDataBrowsingdata(preferences.browsingData.types)
                .setClearDataTabs(preferences.tabs)
                .setClearDataHistory(preferences.history)
                .build()
        }
    }


    suspend fun updateAppearance(appearance: Appearance) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setAppearance(appearance).build()
        }
    }

    suspend fun updateGlobalPrivacyControl(enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setPrivacyGlobalPrivacyControl(enabled).build()
        }
    }

    suspend fun updateFingerprintingProtection(enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setPrivacyFingerprintingProtection(enabled).build()
        }
    }

    suspend fun updateCookiePartitioning(enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setPrivacyCookiePartitioning(enabled).build()
        }
    }

    suspend fun updateStrictTrackingProtection(enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setPrivacyStrictTrackingProtection(enabled).build()
        }
    }

    suspend fun updateTrackingProtectionLevel(level: TrackingProtectionLevel) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setTrackingProtectionLevel(level).build()
        }
    }

    suspend fun updateDohProvider(provider: DoHProvider) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setDohProvider(provider).build()
        }
    }

    suspend fun updateHttpsOnlyLevel(level: HttpsOnlyLevel) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setHttpsOnlyLevel(level).build()
        }
    }

    suspend fun updateAppTrackingProtectionMode(mode: AppTrackingProtectionMode) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setAppTrackingProtectionMode(mode).build()
        }
    }

    suspend fun updateAppTrackingSystemEnabled(enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().setAppTrackingSystemEnabled(enabled).build()
        }
    }

    suspend fun addAppTrackingExcludedPackage(packageName: String) {
        datastore.updateData { prefs ->
            if (prefs.appTrackingExcludedPackagesList.contains(packageName)) {
                prefs
            } else {
                prefs.toBuilder().addAppTrackingExcludedPackages(packageName).build()
            }
        }
    }

    suspend fun removeAppTrackingExcludedPackage(packageName: String) {
        datastore.updateData { prefs ->
            val updated = prefs.appTrackingExcludedPackagesList.filterNot { it == packageName }
            prefs.toBuilder()
                .clearAppTrackingExcludedPackages()
                .addAllAppTrackingExcludedPackages(updated)
                .build()
        }
    }

    suspend fun clearAppTrackingExcludedPackages() {
        datastore.updateData { prefs ->
            prefs.toBuilder().clearAppTrackingExcludedPackages().build()
        }
    }
}
