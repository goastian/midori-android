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

    suspend fun updateHomepageShortcutsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHomepageShortcutsEnabled(enabled).build()
        }
    }

    suspend fun updateHomepagePrivacyStatsEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHomepagePrivacyStatsEnabled(enabled).build()
        }
    }

    suspend fun updateHomepageWeatherEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHomepageWeatherEnabled(enabled).build()
        }
    }

    suspend fun updateHomepageBackgroundPhotoEnabled(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setHomepageBackgroundPhotoEnabled(enabled).build()
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
