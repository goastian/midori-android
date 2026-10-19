package org.midorinext.android.preferences.app

import android.util.Log
import androidx.datastore.core.DataStore
import org.midorinext.android.newtab.HomeSection
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.defaultHomeShortcuts
import org.midorinext.android.newtab.publicHomeUrl
import kotlinx.coroutines.flow.*
import mozilla.components.concept.engine.Engine
import java.io.IOException
import java.util.UUID
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
    suspend fun ensureHomeShortcuts() {
        datastore.updateData { prefs ->
            if (prefs.homeShortcutsInitialized) prefs else prefs.toBuilder()
                .addAllHomeShortcuts(defaultHomeShortcuts()).setHomeShortcutsInitialized(true).build()
        }
    }

    suspend fun saveHomeShortcut(id: String?, title: String, url: String): Boolean {
        val normalized = publicHomeUrl(url) ?: return false
        if (title.isBlank()) return false
        datastore.updateData { prefs ->
            val sites = prefs.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            val shortcut = HomeShortcut.newBuilder().setId(id ?: UUID.randomUUID().toString())
                .setTitle(title.trim()).setUrl(normalized).build()
            if (index >= 0) sites[index] = shortcut else sites.add(shortcut)
            prefs.toBuilder().clearHomeShortcuts().addAllHomeShortcuts(sites)
                .setHomeShortcutsInitialized(true).build()
        }
        return true
    }

    suspend fun deleteHomeShortcut(id: String) {
        datastore.updateData { prefs -> prefs.toBuilder().clearHomeShortcuts()
            .addAllHomeShortcuts(prefs.homeShortcutsList.filterNot { it.id == id }).build() }
    }

    suspend fun moveHomeShortcut(id: String, offset: Int) {
        datastore.updateData { prefs ->
            val sites = prefs.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            if (index < 0 || sites.isEmpty()) prefs else {
                val target = (index + offset).coerceIn(0, sites.lastIndex)
                sites.add(target, sites.removeAt(index))
                prefs.toBuilder().clearHomeShortcuts().addAllHomeShortcuts(sites).build()
            }
        }
    }

    suspend fun updateHomeSection(section: HomeSection, visible: Boolean) {
        datastore.updateData { prefs -> prefs.toBuilder().apply {
            when (section) {
                HomeSection.SHORTCUTS -> setHideHomeShortcuts(!visible)
                HomeSection.CONTINUE -> setHideHomeContinue(!visible)
                HomeSection.RECENT_VISITS -> setHideHomeRecentVisits(!visible)
                HomeSection.STORIES -> setHideHomeStories(!visible)
                HomeSection.PRIVACY -> setHideHomePrivacy(!visible)
            }
        }.build() }
    }

    suspend fun updateOpenBlankNewTab(enabled: Boolean) {
        datastore.updateData { it.toBuilder().setOpenBlankNewTab(enabled).build() }
    }

    suspend fun updateHomeWallpaper(wallpaper: HomeWallpaper) {
        datastore.updateData { preferences -> preferences.toBuilder().apply {
            if (wallpaper == HomeWallpaper.NONE) clearHomeWallpaperId() else setHomeWallpaperId(wallpaper.id)
        }.build() }
    }

    suspend fun updateSearchEngine(engine: SearchEnginePreference) {
        datastore.updateData { it.toBuilder().setSearchEngine(engine).clearCustomDefaultSearchEngineId().build() }
    }

    suspend fun updatePrivateSearchEngine(engine: SearchEnginePreference) {
        datastore.updateData {
            it.toBuilder().setPrivateSearchEngine(engine).clearCustomPrivateSearchEngineId()
                .setUseSeparatePrivateSearchEngine(true).build()
        }
    }

    suspend fun updateCustomSearchEngine(id: String, private: Boolean = false) {
        datastore.updateData { prefs ->
            if (prefs.customSearchEnginesList.none { it.id == id }) prefs
            else if (private) prefs.toBuilder().setCustomPrivateSearchEngineId(id)
                .setUseSeparatePrivateSearchEngine(true).build()
            else prefs.toBuilder().setCustomDefaultSearchEngineId(id).build()
        }
    }

    suspend fun addCustomSearchEngine(name: String, searchUrl: String, suggestionUrl: String = "") {
        datastore.updateData { prefs ->
            prefs.toBuilder().addCustomSearchEngines(
                CustomSearchEngine.newBuilder()
                    .setId(UUID.randomUUID().toString())
                    .setName(name.trim())
                    .setSearchUrlTemplate(searchUrl.trim())
                    .setSuggestionUrlTemplate(suggestionUrl.trim())
                    .build()
            ).build()
        }
    }

    suspend fun removeCustomSearchEngine(id: String) {
        datastore.updateData { prefs ->
            prefs.toBuilder().apply {
                clearCustomSearchEngines()
                addAllCustomSearchEngines(prefs.customSearchEnginesList.filterNot { it.id == id })
                if (prefs.customDefaultSearchEngineId == id) clearCustomDefaultSearchEngineId()
                if (prefs.customPrivateSearchEngineId == id) clearCustomPrivateSearchEngineId()
                clearHiddenSearchEngines()
                addAllHiddenSearchEngines(prefs.hiddenSearchEnginesList.filterNot { it == id })
            }.build()
        }
    }

    suspend fun updateSeparatePrivateSearchEngine(enabled: Boolean) {
        datastore.updateData { it.toBuilder().setUseSeparatePrivateSearchEngine(enabled).build() }
    }

    suspend fun updateAlternativeSearchEngine(engineId: String, visible: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().clearHiddenSearchEngines().addAllHiddenSearchEngines(
                prefs.hiddenSearchEnginesList.filterNot { it == engineId } +
                    if (visible) emptyList() else listOf(engineId)
            ).build()
        }
    }

    suspend fun updateSearchSetting(setting: SearchSetting, enabled: Boolean) {
        datastore.updateData { prefs ->
            prefs.toBuilder().apply {
                when (setting) {
                    SearchSetting.SUGGESTIONS -> disableSearchSuggestions = !enabled
                    SearchSetting.PRIVATE_SUGGESTIONS -> searchSuggestionsInPrivate = enabled
                    SearchSetting.TRENDING -> disableTrendingSuggestions = !enabled
                    SearchSetting.RECENT -> disableRecentSearches = !enabled
                    SearchSetting.HISTORY -> disableHistorySuggestions = !enabled
                    SearchSetting.BOOKMARKS -> disableBookmarkSuggestions = !enabled
                    SearchSetting.TABS -> disableTabSuggestions = !enabled
                    SearchSetting.CLIPBOARD -> disableClipboardSuggestions = !enabled
                    SearchSetting.VOICE -> disableVoiceSearch = !enabled
                    SearchSetting.URL_AUTOCOMPLETE -> disableUrlAutocomplete = !enabled
                }
            }.build()
        }
    }

    suspend fun updateOpenBookmarksInCurrentTab(enabled: Boolean) {
        datastore.updateData { it.toBuilder().setOpenBookmarksInCurrentTab(enabled).build() }
    }

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

    val tabGroupsFlow: Flow<List<SavedTabGroup>> = flow
        .map { it.tabGroupsList }

    suspend fun updateTabGroups(update: (List<SavedTabGroup>) -> List<SavedTabGroup>) {
        datastore.updateData { preferences ->
            preferences.toBuilder()
                .clearTabGroups()
                .addAllTabGroups(update(preferences.tabGroupsList))
                .build()
        }
    }

    suspend fun migrateLegacyTabGroups(groups: List<SavedTabGroup>) {
        datastore.updateData { preferences ->
            if (preferences.tabGroupsMigrated) preferences else preferences.toBuilder()
                .apply {
                    if (preferences.tabGroupsCount == 0) addAllTabGroups(groups)
                }
                .setTabGroupsMigrated(true)
                .build()
        }
    }

    val recentSearchesFlow: Flow<List<String>> = flow
        .map { it.recentSearchesList }

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

    suspend fun cycleTabsView() {
        datastore.updateData { preferences ->
            preferences.toBuilder().setTabsView(preferences.tabsView.nextTabsViewOption()).build()
        }
    }

    suspend fun updateShowTabStrip(enabled: Boolean) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setShowTabStrip(enabled).build()
        }
    }

    suspend fun updateCloseTabsOnExit(enabled: Boolean) {
        datastore.updateData { it.toBuilder().setCloseTabsOnExit(enabled).build() }
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

    suspend fun updateDownloadDirectoryUri(uri: String) {
        datastore.updateData { preferences ->
            preferences.toBuilder().setDownloadDirectoryUri(uri).build()
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

    suspend fun recordRecentSearch(search: String) {
        val normalizedSearch = search.trim()
        if (normalizedSearch.isEmpty()) return

        datastore.updateData { preferences ->
            val recentSearches = buildList {
                add(normalizedSearch)
                addAll(
                    preferences.recentSearchesList.filterNot {
                        it.equals(normalizedSearch, ignoreCase = true)
                    }
                )
            }.take(MAX_RECENT_SEARCHES)

            preferences.toBuilder()
                .clearRecentSearches()
                .addAllRecentSearches(recentSearches)
                .build()
        }
    }

    suspend fun clearRecentSearches() {
        datastore.updateData { preferences ->
            preferences.toBuilder().clearRecentSearches().build()
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

    private companion object {
        const val MAX_RECENT_SEARCHES = 8
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
