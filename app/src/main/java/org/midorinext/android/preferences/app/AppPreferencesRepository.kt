package org.midorinext.android.preferences.app

import android.util.Log
import androidx.datastore.core.DataStore
import org.midorinext.android.newtab.HomeSection
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.defaultHomeShortcuts
import org.midorinext.android.newtab.publicHomeUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import mozilla.components.concept.engine.Engine
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton


private const val LOGTAG: String = "AppPreferencesRepo"

data class ClearDataPreferences(
    val browsingData: Engine.BrowsingData,
    val tabs: Boolean,
    val history: Boolean
)

@Singleton
class AppPreferencesRepository internal constructor(
    private val datastore: DataStore<AppPreferences>,
    snapshotScope: CoroutineScope,
) {
    @Inject
    constructor(datastore: DataStore<AppPreferences>) : this(
        datastore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    suspend fun ensureHomeShortcuts() {
        datastore.updateData { it.withInitializedHomeShortcuts() }
    }

    suspend fun saveHomeShortcut(id: String?, title: String, url: String, parentId: String? = null): Boolean {
        val normalized = publicHomeUrl(url) ?: return false
        if (title.isBlank() || id?.isBlank() == true) return false
        var saved = false
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            val sites = initialized.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            val existing = sites.getOrNull(index)
            val destination = parentId ?: existing?.parentId.orEmpty()
            if (existing?.isFolder == true || !sites.hasHomeFolder(destination)) return@updateData prefs
            val shortcut = (existing?.toBuilder() ?: HomeShortcut.newBuilder())
                .setId(id ?: UUID.randomUUID().toString())
                .setTitle(title.trim()).setUrl(normalized).setParentId(destination).build()
            if (index >= 0) sites[index] = shortcut else sites.add(shortcut)
            saved = true
            initialized.withHomeShortcuts(sites)
        }
        return saved
    }

    suspend fun saveHomeFolder(id: String?, title: String): Boolean {
        if (title.isBlank() || id?.isBlank() == true) return false
        var saved = false
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            val sites = initialized.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            val existing = sites.getOrNull(index)
            if (existing != null && !existing.isFolder) return@updateData prefs
            val folder = HomeShortcut.newBuilder().setId(id ?: UUID.randomUUID().toString())
                .setTitle(title.trim()).setIsFolder(true).build()
            if (index >= 0) sites[index] = folder else sites.add(folder)
            saved = true
            initialized.withHomeShortcuts(sites)
        }
        return saved
    }

    suspend fun addHomeShortcutFolder(title: String, sites: List<Pair<String, String>>): Boolean {
        if (title.isBlank() || sites.any { it.first.isBlank() }) return false
        val normalizedSites = sites.map { (name, url) ->
            name.trim() to (publicHomeUrl(url) ?: return false)
        }.distinctBy { it.second }
        val folderId = UUID.randomUUID().toString()
        val folder = HomeShortcut.newBuilder().setId(folderId).setTitle(title.trim()).setIsFolder(true).build()
        val shortcuts = normalizedSites.map { (name, url) ->
            HomeShortcut.newBuilder().setId(UUID.randomUUID().toString())
                .setTitle(name).setUrl(url).setParentId(folderId).build()
        }
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            initialized.withHomeShortcuts(initialized.homeShortcutsList + folder + shortcuts)
        }
        return true
    }

    suspend fun deleteHomeShortcut(id: String) {
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            initialized.withHomeShortcuts(initialized.homeShortcutsList.filterNot { it.id == id }.map { site ->
                if (site.parentId == id) site.toBuilder().clearParentId().build() else site
            })
        }
    }

    suspend fun moveHomeShortcut(id: String, offset: Int) {
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            val sites = initialized.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            if (index < 0) return@updateData initialized
            val siblingIndices = sites.indices.filter { sites[it].parentId == sites[index].parentId }
            val siblings = siblingIndices.map { sites[it] }.toMutableList()
            val siblingIndex = siblingIndices.indexOf(index)
            val target = (siblingIndex.toLong() + offset).coerceIn(0, siblings.lastIndex.toLong()).toInt()
            siblings.add(target, siblings.removeAt(siblingIndex))
            siblingIndices.forEachIndexed { position, siteIndex -> sites[siteIndex] = siblings[position] }
            initialized.withHomeShortcuts(sites)
        }
    }

    suspend fun moveHomeShortcutToFolder(id: String, parentId: String): Boolean {
        var moved = false
        datastore.updateData { prefs ->
            val initialized = prefs.withInitializedHomeShortcuts()
            val sites = initialized.homeShortcutsList.toMutableList()
            val index = sites.indexOfFirst { it.id == id }
            if (index < 0 || !sites.hasHomeFolder(parentId)) return@updateData prefs
            val site = sites[index]
            if (site.isFolder && parentId.isNotEmpty()) return@updateData prefs
            moved = true
            if (site.parentId == parentId) return@updateData initialized
            sites.removeAt(index)
            sites.add(site.toBuilder().setParentId(parentId).build())
            initialized.withHomeShortcuts(sites)
        }
        return moved
    }

    private fun AppPreferences.withInitializedHomeShortcuts(): AppPreferences {
        if (homeShortcutsInitialized) return this
        return toBuilder().apply {
            if (homeShortcutsCount == 0) addAllHomeShortcuts(defaultHomeShortcuts())
        }.setHomeShortcutsInitialized(true).build()
    }

    private fun AppPreferences.withHomeShortcuts(shortcuts: List<HomeShortcut>): AppPreferences =
        toBuilder().clearHomeShortcuts().addAllHomeShortcuts(shortcuts).build()

    private fun List<HomeShortcut>.hasHomeFolder(id: String): Boolean =
        id.isEmpty() || any { it.id == id && it.isFolder && it.parentId.isEmpty() }

    suspend fun updateHomeShortcutShape(shape: HomeShortcutShape) {
        if (shape == HomeShortcutShape.UNRECOGNIZED) return
        datastore.updateData { it.toBuilder().setHomeShortcutShape(shape).build() }
    }

    suspend fun updateHomeLargeShortcuts(enabled: Boolean) {
        datastore.updateData { it.toBuilder().setHomeLargeShortcuts(enabled).build() }
    }

    suspend fun updateHomeAddShortcut(visible: Boolean) {
        datastore.updateData { it.toBuilder().setHideHomeAddShortcut(!visible).build() }
    }

    suspend fun updateHomeLogo(visible: Boolean) {
        datastore.updateData { it.toBuilder().setHideHomeLogo(!visible).build() }
    }

    suspend fun updateHomeCustomizeShortcut(visible: Boolean) {
        datastore.updateData { it.toBuilder().setHideHomeCustomizeShortcut(!visible).build() }
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
                emit(AppPreferencesSerializer.defaultValue)
            } else {
                throw exception
            }
        }

    val snapshot: StateFlow<AppPreferences?> = kotlinx.coroutines.flow.flow {
        var hasSnapshot = false
        emitAll(datastore.data.onEach { hasSnapshot = true }.retryWhen { cause, _ ->
            if (cause !is IOException) return@retryWhen false
            if (!hasSnapshot) {
                emit(AppPreferencesSerializer.defaultValue)
                hasSnapshot = true
            }
            delay(1_000)
            true
        })
    }.stateIn(
        scope = snapshotScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

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

    suspend fun updateTabGroupsAndColors(
        update: (List<SavedTabGroup>, Map<String, Int>) -> Pair<List<SavedTabGroup>, Map<String, Int>>,
    ) {
        datastore.updateData { preferences ->
            val (groups, colors) = update(preferences.tabGroupsList, preferences.tabGroupColorsMap)
            preferences.toBuilder().clearTabGroups().addAllTabGroups(groups)
                .clearTabGroupColors().putAllTabGroupColors(colors).build()
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
