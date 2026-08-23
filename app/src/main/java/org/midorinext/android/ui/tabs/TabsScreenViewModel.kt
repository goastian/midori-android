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
import java.util.UUID

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

    private val recentlyClosedTabs = MutableStateFlow<List<ClosedTabSnapshot>>(emptyList())

    val recentlyClosedCount: StateFlow<Int> = recentlyClosedTabs
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    val smartTabs = combine(tabs, tabGroups, appPreferencesRepository.tabGroupColorsFlow) {
            allTabs, groups, colors ->
        buildSmartTabs(allTabs, groups, colors)
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
        // Protobuf exposes unknown persisted enum values as UNRECOGNIZED. That enum
        // cannot be used by Compose state saving (its getNumber() throws), so fall
        // back to the default before it reaches the UI.
        .map { prefs ->
            when (prefs.tabsView) {
                TabsViewOption.LIST -> TabsViewOption.LIST
                else -> TabsViewOption.GRID
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TabsViewOption.GRID
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
        } else if (!midoriUseCases.isNewTabEnabled && openBlankNewTab.value) {
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

    fun nextGroupName(): String {
        val existingGroups = store.state.tabPartitions[TAB_GROUPS_PARTITION]?.tabGroups.orEmpty()
        return "Group ${existingGroups.size + 1}"
    }

    fun nextGroupColor(): TabGroupColor = TabGroupColor.entries[tabGroups.value.size % TabGroupColor.entries.size]

    /** Creates one named group from explicitly selected normal tabs, regardless of their sites. */
    fun groupTabs(tabIds: Set<String>, name: String, color: TabGroupColor): Int {
        val selectedTabIds = store.state.tabs
            .filter { !it.content.private && it.id in tabIds }
            .mapTo(linkedSetOf()) { it.id }
        if (selectedTabIds.size < 2) return 0

        val existingGroups = store.state.tabPartitions[TAB_GROUPS_PARTITION]?.tabGroups.orEmpty()
        detachTabsFromGroups(selectedTabIds)

        val groupId = "group:${UUID.randomUUID()}"
        tabsUseCases.addTabGroup(
            TabGroup(id = groupId, name = name.trim().ifBlank { "Group ${existingGroups.size + 1}" })
        )
        tabsUseCases.addTabsInGroup(groupId, selectedTabIds)
        viewModelScope.launch { appPreferencesRepository.updateTabGroupColor(groupId, color.value) }
        return selectedTabIds.size
    }

    fun renameGroup(groupId: String, name: String): Boolean {
        val group = findGroup(groupId) ?: return false
        val updatedName = name.trim().ifBlank { group.name }
        if (updatedName == group.name) return true

        // Android Components has no update action for TabGroup metadata. Replacing the group
        // with the same ID preserves the membership and any UI color stored by Midori.
        tabsUseCases.removeTabGroup(groupId)
        tabsUseCases.addTabGroup(group.copy(name = updatedName))
        return true
    }

    fun updateGroupColor(groupId: String, color: TabGroupColor) {
        if (findGroup(groupId) != null) {
            viewModelScope.launch { appPreferencesRepository.updateTabGroupColor(groupId, color.value) }
        }
    }

    fun addTabsToGroup(groupId: String, tabIds: Set<String>): Int {
        if (findGroup(groupId) == null) return 0
        val selectedTabIds = store.state.tabs
            .filter { !it.content.private && it.id in tabIds }
            .mapTo(linkedSetOf()) { it.id }
        if (selectedTabIds.isEmpty()) return 0

        detachTabsFromGroups(selectedTabIds, exceptGroupId = groupId)
        tabsUseCases.addTabsInGroup(groupId, selectedTabIds)
        return selectedTabIds.size
    }

    fun removeTabFromGroup(groupId: String, tabId: String): Boolean {
        val group = findGroup(groupId) ?: return false
        if (tabId !in group.tabIds) return false

        if (group.tabIds.size <= 2) {
            // Keep the tab tray meaningful: removing one of two tabs dissolves the group and
            // leaves the other tab available as a regular tab.
            tabsUseCases.removeTabGroup(groupId)
            viewModelScope.launch { appPreferencesRepository.removeTabGroupColor(groupId) }
        } else {
            tabsUseCases.removeTabsInGroup(groupId, setOf(tabId))
        }
        return true
    }

    fun deleteGroup(groupId: String): Int {
        val group = findGroup(groupId) ?: return 0
        val groupedTabs = store.state.tabs.filter { it.id in group.tabIds }
        rememberClosedTabs(groupedTabs)
        tabsUseCases.closeTabGroup(groupId, group.tabIds.toList())
        viewModelScope.launch { appPreferencesRepository.removeTabGroupColor(groupId) }
        return groupedTabs.size
    }

    private fun findGroup(groupId: String): TabGroup? =
        store.state.tabPartitions[TAB_GROUPS_PARTITION]?.tabGroups?.firstOrNull { it.id == groupId }

    private fun detachTabsFromGroups(tabIds: Set<String>, exceptGroupId: String? = null) {
        store.state.tabPartitions[TAB_GROUPS_PARTITION]?.tabGroups.orEmpty().forEach { group ->
            if (group.id == exceptGroupId) return@forEach
            val movedTabIds = group.tabIds.intersect(tabIds)
            if (movedTabIds.isEmpty()) return@forEach

            val remainingTabIds = group.tabIds - movedTabIds
            if (remainingTabIds.size < 2) {
                tabsUseCases.removeTabGroup(group.id)
                viewModelScope.launch { appPreferencesRepository.removeTabGroupColor(group.id) }
            } else {
                tabsUseCases.removeTabsInGroup(group.id, movedTabIds)
            }
        }
    }

    private fun buildSmartTabs(
        allTabs: List<mozilla.components.browser.state.state.TabSessionState>,
        groups: List<TabGroup>,
        groupColors: Map<String, Int>
    ): SmartTabsState {
        val tabsById = allTabs.associateBy { it.id }
        val visibleGroups = groups.mapIndexedNotNull { index, group ->
            val groupTabs = group.tabIds.mapNotNull { tabsById[it] }
            if (groupTabs.size > 1) {
                SmartTabGroup(
                    id = group.id,
                    name = group.name.ifBlank { "Group" },
                    color = TabGroupColor.fromValue(
                        groupColors[group.id] ?: TabGroupColor.entries[index % TabGroupColor.entries.size].value
                    ),
                    tabs = groupTabs.sortedByDescending { maxOf(it.lastAccess, it.createdAt) }
                )
            } else {
                null
            }
        }

        val groupedIds = visibleGroups.flatMap { group -> group.tabs.map { it.id } }.toSet()
        val ungroupedTabs = allTabs.filterNot { it.id in groupedIds }
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
    val groups: List<SmartTabGroup> = emptyList()
) {
    val isEmpty: Boolean
        get() = activeTabs.isEmpty() &&
            inactiveTabs.isEmpty() &&
            groups.isEmpty()
}

data class SmartTabGroup(
    val id: String,
    val name: String,
    val color: TabGroupColor,
    val tabs: List<mozilla.components.browser.state.state.TabSessionState>
)

enum class TabGroupColor(val value: Int) {
    BLUE(0),
    TEAL(1),
    GREEN(2),
    ORANGE(3),
    RED(4),
    PURPLE(5);

    companion object {
        fun fromValue(value: Int?): TabGroupColor = entries.firstOrNull { it.value == value } ?: BLUE
    }
}

private data class ClosedTabSnapshot(
    val title: String,
    val url: String,
    val private: Boolean
)

private fun mozilla.components.browser.state.state.TabSessionState.isInactive(): Boolean {
    val lastActiveTime = maxOf(lastAccess, createdAt)
    return System.currentTimeMillis() - lastActiveTime > INACTIVE_TAB_AGE_MS
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
