package org.midorinext.android.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.SavedTabGroup
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.lib.state.ext.flow
import javax.inject.Inject
import java.util.UUID

private const val INACTIVE_TAB_AGE_MS = 14L * 24L * 60L * 60L * 1000L

@HiltViewModel
class TabsScreenViewModel @Inject constructor(
    private val store: BrowserStore,
    private val tabsUseCases: TabsUseCases,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val midoriUseCases: MidoriUseCases,
    val thumbnailStorage: ThumbnailStorage,
    val browserIcons: BrowserIcons,
): ViewModel() {
    val tabs = store.flow()
        .map { state -> state.tabs }
        // BrowserState also changes for loading progress, history and prompts. Those fields are
        // not rendered by the tray, so they must not invalidate every visible tab card.
        .distinctUntilChanged { previous, current -> previous.hasSameTrayContentAs(current) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = listOf()
        )

    private val tabGroups = appPreferencesRepository.tabGroupsFlow
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val restoreComplete = store.flow()
        .map { state -> state.restoreComplete }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    init {
        // Mozilla 157 no longer owns group membership. Remove IDs for tabs closed outside the
        // tray, but wait for session restoration before comparing against the browser store.
        viewModelScope.launch {
            val openTabIds = store.flow()
                .map { state -> state.restoreComplete to state.tabs.mapTo(hashSetOf()) { it.id } }
                .distinctUntilChanged()
            combine(openTabIds, tabGroups) { (restored, openIds), groups ->
                Triple(restored, openIds, groups)
            }.collectLatest { (restored, openIds, groups) ->
                if (!restored) return@collectLatest
                val validGroups = groups.keepOpenTabs(openIds)
                if (validGroups == groups) return@collectLatest
                appPreferencesRepository.updateTabGroups { it.keepOpenTabs(openIds) }
                val validGroupIds = validGroups.mapTo(hashSetOf()) { it.id }
                groups.filterNot { it.id in validGroupIds }.forEach {
                    appPreferencesRepository.removeTabGroupColor(it.id)
                }
            }
        }
    }

    val canUndoClose = store.flow()
        .map { state -> state.undoHistory.tabs.isNotEmpty() }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = false
        )

    private val recentlyClosedTabs = RecentlyClosedTabs(tabsUseCases)

    val recentlyClosedCount: StateFlow<Int> = recentlyClosedTabs.count
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = 0
        )

    val smartTabs = combine(tabs, tabGroups, appPreferencesRepository.tabGroupColorsFlow) {
            allTabs, groups, colors ->
        buildSmartTabs(allTabs, groups, colors)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SmartTabsState()
        )

    val selectedTabId = store.flow()
        .map { state -> state.selectedTabId }
        .distinctUntilChanged()
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
        } else {
            midoriUseCases.openMidoriPage(private = false)
        }
    }

    fun reopenRecentlyClosed(): Boolean = recentlyClosedTabs.reopen()

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
        return "Group ${tabGroups.value.size + 1}"
    }

    fun nextGroupColor(): TabGroupColor = TabGroupColor.entries[tabGroups.value.size % TabGroupColor.entries.size]

    /** Creates one named group from explicitly selected normal tabs, regardless of their sites. */
    fun groupTabs(tabIds: Set<String>, name: String, color: TabGroupColor): Int {
        val selectedTabIds = store.state.tabs
            .filter { !it.content.private && it.id in tabIds }
            .mapTo(linkedSetOf()) { it.id }
        if (selectedTabIds.size < 2) return 0

        val groupId = "group:${UUID.randomUUID()}"
        viewModelScope.launch {
            appPreferencesRepository.updateTabGroups { groups ->
                groups.withoutTabIds(selectedTabIds) + SavedTabGroup.newBuilder()
                    .setId(groupId)
                    .setName(name.trim().ifBlank { "Group ${groups.size + 1}" })
                    .addAllTabIds(selectedTabIds)
                    .build()
            }
            appPreferencesRepository.updateTabGroupColor(groupId, color.value)
        }
        return selectedTabIds.size
    }

    fun renameGroup(groupId: String, name: String): Boolean {
        val group = findGroup(groupId) ?: return false
        val updatedName = name.trim().ifBlank { group.name }
        if (updatedName == group.name) return true

        viewModelScope.launch {
            appPreferencesRepository.updateTabGroups { groups ->
                groups.map { if (it.id == groupId) it.toBuilder().setName(updatedName).build() else it }
            }
        }
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

        viewModelScope.launch {
            appPreferencesRepository.updateTabGroups { groups ->
                groups.withoutTabIds(selectedTabIds, exceptGroupId = groupId)
                    .map { group ->
                        if (group.id == groupId) {
                            group.toBuilder().addAllTabIds(selectedTabIds - group.tabIdsList.toSet()).build()
                        } else group
                    }
            }
        }
        return selectedTabIds.size
    }

    fun removeTabFromGroup(groupId: String, tabId: String): Boolean {
        val group = findGroup(groupId) ?: return false
        if (tabId !in group.tabIdsList) return false

        if (group.tabIdsCount <= 2) {
            // Keep the tab tray meaningful: removing one of two tabs dissolves the group and
            // leaves the other tab available as a regular tab.
            viewModelScope.launch {
                appPreferencesRepository.updateTabGroups { groups -> groups.filterNot { it.id == groupId } }
                appPreferencesRepository.removeTabGroupColor(groupId)
            }
        } else {
            viewModelScope.launch {
                appPreferencesRepository.updateTabGroups { groups ->
                    groups.map {
                        if (it.id == groupId) it.toBuilder().clearTabIds()
                            .addAllTabIds(it.tabIdsList.filterNot { id -> id == tabId }).build()
                        else it
                    }
                }
            }
        }
        return true
    }

    fun deleteGroup(groupId: String): Int {
        val group = findGroup(groupId) ?: return 0
        val groupedTabs = store.state.tabs.filter { it.id in group.tabIdsList }
        rememberClosedTabs(groupedTabs)
        tabsUseCases.removeTabs(group.tabIdsList)
        viewModelScope.launch {
            appPreferencesRepository.updateTabGroups { groups -> groups.filterNot { it.id == groupId } }
            appPreferencesRepository.removeTabGroupColor(groupId)
        }
        return groupedTabs.size
    }

    private fun findGroup(groupId: String): SavedTabGroup? =
        tabGroups.value.firstOrNull { it.id == groupId }

    private fun buildSmartTabs(
        allTabs: List<mozilla.components.browser.state.state.TabSessionState>,
        groups: List<SavedTabGroup>,
        groupColors: Map<String, Int>
    ): SmartTabsState {
        val tabsById = allTabs.associateBy { it.id }
        val visibleGroups = groups.mapIndexedNotNull { index, group ->
            val groupTabs = group.tabIdsList.mapNotNull { tabsById[it] }
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

        val groupedIds = visibleGroups.flatMapTo(hashSetOf()) { group ->
            group.tabs.map { tab -> tab.id }
        }
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
        recentlyClosedTabs.remember(tabs)
    }
}

private fun List<SavedTabGroup>.withoutTabIds(
    tabIds: Set<String>,
    exceptGroupId: String? = null,
): List<SavedTabGroup> = mapNotNull { group ->
    if (group.id == exceptGroupId) return@mapNotNull group
    val remainingIds = group.tabIdsList.filterNot { it in tabIds }
    when {
        remainingIds.size == group.tabIdsCount -> group
        remainingIds.size < 2 -> null
        else -> group.toBuilder().clearTabIds().addAllTabIds(remainingIds).build()
    }
}

private fun List<SavedTabGroup>.keepOpenTabs(openIds: Set<String>): List<SavedTabGroup> =
    mapNotNull { group ->
        val remainingIds = group.tabIdsList.filter { it in openIds }
        when {
            remainingIds.size == group.tabIdsCount -> group
            remainingIds.size < 2 -> null
            else -> group.toBuilder().clearTabIds().addAllTabIds(remainingIds).build()
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
) {
    // Drag hit-testing reads this on every pointer event. Materialize it once per state update
    // instead of allocating a list repeatedly while the finger moves.
    val tabIds: Set<String> = tabs.mapTo(hashSetOf()) { tab -> tab.id }
}

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

private fun mozilla.components.browser.state.state.TabSessionState.isInactive(): Boolean {
    val lastActiveTime = maxOf(lastAccess, createdAt)
    return System.currentTimeMillis() - lastActiveTime > INACTIVE_TAB_AGE_MS
}

private fun List<mozilla.components.browser.state.state.TabSessionState>.hasSameTrayContentAs(
    other: List<mozilla.components.browser.state.state.TabSessionState>,
): Boolean {
    if (size != other.size) return false

    return indices.all { index ->
        val previous = this[index]
        val current = other[index]
        previous.id == current.id &&
            previous.content.url == current.content.url &&
            previous.content.title == current.content.title &&
            previous.content.private == current.content.private &&
            previous.content.icon === current.content.icon &&
            previous.lastAccess == current.lastAccess &&
            previous.createdAt == current.createdAt
    }
}
