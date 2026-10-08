package org.midorinext.android.ui.tabs

import androidx.compose.runtime.saveable.listSaver
import mozilla.components.browser.state.state.TabSessionState
import org.midorinext.android.preferences.app.TabsViewOption

enum class TabsPage(val isPrivate: Boolean) {
    NORMAL(isPrivate = false),
    PRIVATE(isPrivate = true),
}

sealed interface TabsMode {
    data object Browsing : TabsMode

    data class Searching(val query: String = "") : TabsMode

    data class Selecting(
        val tabIds: Set<String> = emptySet(),
        val targetGroupId: String? = null,
    ) : TabsMode
}

data class TabsUiState(
    val page: TabsPage,
    val mode: TabsMode = TabsMode.Browsing,
)

sealed interface TabsUiAction {
    data class SelectPage(val page: TabsPage) : TabsUiAction
    data object ToggleSearch : TabsUiAction
    data class UpdateSearch(val query: String) : TabsUiAction
    data class BeginSelection(
        val tabIds: Set<String> = emptySet(),
        val targetGroupId: String? = null,
    ) : TabsUiAction

    data class ToggleTabSelection(val tabId: String) : TabsUiAction
    data object ExitTransientMode : TabsUiAction
}

fun TabsUiState.reduce(action: TabsUiAction): TabsUiState = when (action) {
    is TabsUiAction.SelectPage -> copy(
        page = action.page,
        // Selection only applies to the page where it started. Search is intentionally retained
        // so users can compare the same query between normal and private tabs.
        mode = if (mode is TabsMode.Selecting) TabsMode.Browsing else mode,
    )

    TabsUiAction.ToggleSearch -> copy(
        mode = if (mode is TabsMode.Searching) TabsMode.Browsing else TabsMode.Searching(),
    )

    is TabsUiAction.UpdateSearch -> {
        val currentMode = mode
        if (currentMode is TabsMode.Searching) {
            copy(mode = currentMode.copy(query = action.query))
        } else {
            this
        }
    }

    is TabsUiAction.BeginSelection -> copy(
        mode = TabsMode.Selecting(
            tabIds = action.tabIds,
            targetGroupId = action.targetGroupId,
        ),
    )

    is TabsUiAction.ToggleTabSelection -> {
        val currentMode = mode
        if (currentMode is TabsMode.Selecting) {
            copy(
                mode = currentMode.copy(
                    tabIds = currentMode.tabIds.let { selected ->
                        if (action.tabId in selected) {
                            selected - action.tabId
                        } else {
                            selected + action.tabId
                        }
                    },
                ),
            )
        } else {
            this
        }
    }

    TabsUiAction.ExitTransientMode -> copy(mode = TabsMode.Browsing)
}

val TabsUiStateSaver = listSaver<TabsUiState, String>(
    save = { state ->
        when (val mode = state.mode) {
            TabsMode.Browsing -> listOf(state.page.name, "browsing")
            is TabsMode.Searching -> listOf(state.page.name, "searching", mode.query)
            is TabsMode.Selecting -> buildList {
                add(state.page.name)
                add("selecting")
                add(mode.targetGroupId.orEmpty())
                addAll(mode.tabIds.sorted())
            }
        }
    },
    restore = { saved ->
        val page = saved.firstOrNull()
            ?.let { name -> TabsPage.entries.firstOrNull { it.name == name } }
            ?: TabsPage.NORMAL
        val mode = when (saved.getOrNull(1)) {
            "searching" -> TabsMode.Searching(query = saved.getOrNull(2).orEmpty())
            "selecting" -> TabsMode.Selecting(
                targetGroupId = saved.getOrNull(2)?.takeIf(String::isNotEmpty),
                tabIds = saved.drop(3).toSet(),
            )
            else -> TabsMode.Browsing
        }
        TabsUiState(page = page, mode = mode)
    },
)

/**
 * Scopes and filters the tray once before lazy layout composition. Keeping this work outside each
 * card avoids re-running matching logic while thumbnails or unrelated browser state is updated.
 */
fun SmartTabsState.forPageAndQuery(page: TabsPage, rawQuery: String): SmartTabsState {
    val query = rawQuery.trim()
    val pageTabs: (TabSessionState) -> Boolean = { tab ->
        tab.content.private == page.isPrivate
    }
    val matchesQuery: (TabSessionState) -> Boolean = { tab ->
        query.isEmpty() ||
            tab.content.title.contains(query, ignoreCase = true) ||
            tab.content.url.contains(query, ignoreCase = true)
    }

    val filteredGroups = if (page.isPrivate) {
        emptyList()
    } else {
        groups.mapNotNull { group ->
            val groupMatches = query.isNotEmpty() && group.name.contains(query, ignoreCase = true)
            val matchingTabs = group.tabs.filter { tab ->
                pageTabs(tab) && (groupMatches || matchesQuery(tab))
            }
            group.copy(tabs = matchingTabs).takeIf { matchingTabs.isNotEmpty() }
        }
    }

    return SmartTabsState(
        activeTabs = activeTabs.filter { pageTabs(it) && matchesQuery(it) },
        inactiveTabs = inactiveTabs.filter { pageTabs(it) && matchesQuery(it) },
        groups = filteredGroups,
    )
}

internal fun SmartTabsState.selectedItemIndex(
    selectedTabId: String?,
    tabsViewOption: TabsViewOption,
): Int {
    if (selectedTabId == null) return -1
    val groupIndex = groups.indexOfFirst { group -> group.tabs.any { it.id == selectedTabId } }
    if (groupIndex >= 0) return groupIndex

    val hasActiveHeader = activeTabs.isNotEmpty() && when (tabsViewOption) {
        TabsViewOption.LIST -> groups.isNotEmpty() || inactiveTabs.isNotEmpty()
        else -> groups.isEmpty()
    }
    val activeStart = groups.size + if (hasActiveHeader) 1 else 0
    val activeIndex = activeTabs.indexOfFirst { it.id == selectedTabId }
    if (activeIndex >= 0) return activeStart + activeIndex

    val inactiveIndex = inactiveTabs.indexOfFirst { it.id == selectedTabId }
    return if (inactiveIndex >= 0) activeStart + activeTabs.size + 1 + inactiveIndex else -1
}
