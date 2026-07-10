package org.midorinext.android.ui.tabs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.state.TabGroup
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import javax.inject.Inject

private const val TAB_GROUPS_PARTITION = "TAB_GROUPS"
private const val INACTIVE_TAB_AGE_MS = 14L * 24L * 60L * 60L * 1000L
private const val MAX_RECENTLY_CLOSED = 10

@HiltViewModel
class TabsScreenViewModel @Inject constructor(
    private val store: BrowserStore,
    private val tabsUseCases: TabsUseCases,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val midoriUseCases: MidoriUseCases,
    val thumbnailStorage: ThumbnailStorage,
    val browserIcons: BrowserIcons,
    val contentBlockerState: ContentBlockerState
): ViewModel() {
    val tabs = store.flow()
        .map { state -> state.tabs }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = listOf()
        )

    private val tabGroups = store.flow()
        .map { state -> state.tabPartitions[TAB_GROUPS_PARTITION]?.tabGroups.orEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val restoreComplete = store.flow()
        .map { state -> state.restoreComplete }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    val canUndoClose = store.flow()
        .map { state -> state.undoHistory.tabs.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val searchQuery = MutableStateFlow("")

    private val recentlyClosedTabs = MutableStateFlow<List<ClosedTabSnapshot>>(emptyList())

    val tabSearchQuery: StateFlow<String> = searchQuery.asStateFlow()

    val recentlyClosedCount: StateFlow<Int> = recentlyClosedTabs
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    val smartTabs = combine(tabs, tabGroups, searchQuery) { allTabs, groups, query ->
        buildSmartTabs(allTabs, groups, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SmartTabsState()
    )

    val selectedTabId = store.flow()
        .map { state -> state.selectedTabId }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val tabsViewOption = appPreferencesRepository.flow
        .map { prefs -> prefs.tabsView }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TabsViewOption.UNRECOGNIZED
        )

    private val openBlankNewTab = appPreferencesRepository.flow
        .map { prefs -> prefs.openBlankNewTab }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    fun updateTabsViewOption(option: TabsViewOption) {
        viewModelScope.launch { appPreferencesRepository.updateTabsView(option) }
    }

    fun updateTabSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun removeTabs(private: Boolean = false) {
        rememberClosedTabs(store.state.tabs.filter { it.content.private == private })
        if (private) {
            tabsUseCases.removePrivateTabs.invoke()
        } else {
            tabsUseCases.removeNormalTabs.invoke()
        }
    }

    val selectTab = tabsUseCases.selectTab

    fun removeTab(tabId: String) {
        store.state.tabs.firstOrNull { it.id == tabId }?.let { rememberClosedTabs(listOf(it)) }
        tabsUseCases.removeTab(tabId)
    }

    fun undoLastClose() {
        tabsUseCases.undo()
    }

    fun openNewTab(private: Boolean) {
        if (private) {
            tabsUseCases.addTab("", selectTab = true, private = true)
        } else if (openBlankNewTab.value) {
            tabsUseCases.addTab("", selectTab = true, private = false)
        } else {
            midoriUseCases.openMidoriPage(private = false)
        }
    }

    fun reopenRecentlyClosed(): Boolean {
        val closed = recentlyClosedTabs.value.firstOrNull() ?: return false
        tabsUseCases.addTab(
            url = closed.url,
            selectTab = true,
            title = closed.title,
            private = closed.private
        )
        recentlyClosedTabs.value = recentlyClosedTabs.value.drop(1)
        return true
    }

    fun closeDuplicateTabs(private: Boolean): Int {
        val duplicates = store.state.tabs
            .filter { it.content.private == private }
            .filter { it.content.url.isNotBlank() }
            .groupBy { canonicalUrl(it.content.url) }
            .values
            .flatMap { matchingTabs ->
                matchingTabs
                    .sortedByDescending { maxOf(it.lastAccess, it.createdAt) }
                    .drop(1)
            }

        if (duplicates.isEmpty()) {
            return 0
        }

        rememberClosedTabs(duplicates)
        tabsUseCases.removeTabs(duplicates.map { it.id })
        return duplicates.size
    }

    fun groupTabsBySite(private: Boolean): Int {
        if (private) {
            return 0
        }

        val groupsByHost = store.state.tabs
            .filter { !it.content.private }
            .filter { it.content.url.isNotBlank() }
            .groupBy { hostForGrouping(it.content.url) }
            .filterKeys { it.isNotBlank() }
            .filterValues { it.size > 1 }

        val existingGroups = store.state.tabPartitions[TAB_GROUPS_PARTITION]
            ?.tabGroups
            ?.associateBy { it.id }
            .orEmpty()

        var groupedTabs = 0
        groupsByHost.forEach { (host, hostTabs) ->
            val groupId = "site:$host"
            if (existingGroups[groupId] == null) {
                tabsUseCases.addTabGroup(TabGroup(id = groupId, name = host))
            }

            val currentTabIds = existingGroups[groupId]?.tabIds.orEmpty()
            val tabIds = hostTabs.map { it.id }.filterNot { it in currentTabIds }.toSet()
            if (tabIds.isNotEmpty()) {
                tabsUseCases.addTabsInGroup(groupId, tabIds)
                groupedTabs += tabIds.size
            }
        }

        return groupedTabs
    }

    private fun buildSmartTabs(
        allTabs: List<mozilla.components.browser.state.state.TabSessionState>,
        groups: List<TabGroup>,
        query: String
    ): SmartTabsState {
        val trimmedQuery = query.trim()
        val searchedTabs = allTabs.filter { tab ->
            trimmedQuery.isBlank() ||
                tab.content.title.contains(trimmedQuery, ignoreCase = true) ||
                tab.content.url.contains(trimmedQuery, ignoreCase = true)
        }

        if (trimmedQuery.isNotBlank()) {
            return SmartTabsState(searchResults = searchedTabs.reversed(), searchActive = true)
        }

        val tabsById = searchedTabs.associateBy { it.id }
        val visibleGroups = groups.mapNotNull { group ->
            val groupTabs = group.tabIds.mapNotNull { tabsById[it] }
            if (groupTabs.size > 1) {
                SmartTabGroup(
                    id = group.id,
                    name = group.name.ifBlank { "Group" },
                    tabs = groupTabs.sortedByDescending { maxOf(it.lastAccess, it.createdAt) }
                )
            } else {
                null
            }
        }

        val groupedIds = visibleGroups.flatMap { group -> group.tabs.map { it.id } }.toSet()
        val ungroupedTabs = searchedTabs.filterNot { it.id in groupedIds }
        val inactiveTabs = ungroupedTabs.filter { it.isInactive() }
        val activeTabs = ungroupedTabs.filterNot { it.isInactive() }

        return SmartTabsState(
            activeTabs = activeTabs.reversed(),
            inactiveTabs = inactiveTabs.sortedByDescending { maxOf(it.lastAccess, it.createdAt) },
            groups = visibleGroups
        )
    }

    private fun rememberClosedTabs(tabs: List<mozilla.components.browser.state.state.TabSessionState>) {
        if (tabs.isEmpty()) {
            return
        }

        val snapshots = tabs
            .filter { it.content.url.isNotBlank() }
            .map { tab ->
                ClosedTabSnapshot(
                    title = tab.content.title,
                    url = tab.content.url,
                    private = tab.content.private
                )
            }

        if (snapshots.isEmpty()) {
            return
        }

        recentlyClosedTabs.value = (snapshots + recentlyClosedTabs.value)
            .distinctBy { "${it.private}:${canonicalUrl(it.url)}" }
            .take(MAX_RECENTLY_CLOSED)
    }
}

data class SmartTabsState(
    val activeTabs: List<mozilla.components.browser.state.state.TabSessionState> = emptyList(),
    val inactiveTabs: List<mozilla.components.browser.state.state.TabSessionState> = emptyList(),
    val groups: List<SmartTabGroup> = emptyList(),
    val searchResults: List<mozilla.components.browser.state.state.TabSessionState> = emptyList(),
    val searchActive: Boolean = false
) {
    val isEmpty: Boolean
        get() = activeTabs.isEmpty() &&
            inactiveTabs.isEmpty() &&
            groups.isEmpty() &&
            searchResults.isEmpty()
}

data class SmartTabGroup(
    val id: String,
    val name: String,
    val tabs: List<mozilla.components.browser.state.state.TabSessionState>
)

private data class ClosedTabSnapshot(
    val title: String,
    val url: String,
    val private: Boolean
)

private fun mozilla.components.browser.state.state.TabSessionState.isInactive(): Boolean {
    val lastActiveTime = maxOf(lastAccess, createdAt)
    return System.currentTimeMillis() - lastActiveTime > INACTIVE_TAB_AGE_MS
}

private fun hostForGrouping(url: String): String {
    val host = runCatching { Uri.parse(url).host }.getOrNull()
        ?.lowercase()
        ?.removePrefix("www.")
        .orEmpty()
    return host
}

private fun canonicalUrl(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url.trim()
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
    val path = uri.path.orEmpty().trimEnd('/')
    val query = uri.query.orEmpty()
    return buildString {
        append(scheme)
        append("://")
        append(host)
        append(path)
        if (query.isNotBlank()) {
            append('?')
            append(query)
        }
    }.ifBlank { url.trim() }
}
