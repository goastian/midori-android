package org.midorinext.android.ui.tabs

import org.midorinext.android.preferences.app.SavedTabGroup

internal data class TabGroupingChange(
    val groupId: String,
    val before: List<SavedTabGroup>,
    val after: List<SavedTabGroup>,
    val beforeColors: Map<String, Int>,
    val afterColors: Map<String, Int>,
) {
    fun undo(
        groups: List<SavedTabGroup>,
        colors: Map<String, Int>,
        openIds: Set<String>,
    ): Pair<List<SavedTabGroup>, Map<String, Int>> {
        val oldById = before.associateBy { it.id }
        val newById = after.associateBy { it.id }
        val changedIds = (oldById.keys + newById.keys).filterTo(hashSetOf()) { oldById[it] != newById[it] }
        fun List<SavedTabGroup>.onlyOpen() = mapNotNull { group ->
            val ids = group.tabIdsList.filter { it in openIds }
            if (ids.size < 2) null else group.toBuilder().clearTabIds().addAllTabIds(ids).build()
        }
        val expected = after.filter { it.id in changedIds }.onlyOpen()
        val actual = groups.filter { it.id in changedIds }
        if (actual != expected || changedIds.any { colors[it] != afterColors[it] }) return groups to colors
        val restored = before.filter { it.id in changedIds }.onlyOpen()
        val restoredTabIds = restored.flatMapTo(hashSetOf()) { it.tabIdsList }
        if (groups.any { it.id !in changedIds && it.tabIdsList.any(restoredTabIds::contains) }) return groups to colors
        val result = groups.filterNot { it.id in changedIds }.toMutableList()
        restored.forEach { group -> result.add(before.indexOfFirst { it.id == group.id }.coerceIn(0, result.size), group) }
        return result to (colors - changedIds + beforeColors.filterKeys { it in changedIds })
    }
}

internal fun changedTabGrouping(
    groups: List<SavedTabGroup>,
    colors: Map<String, Int>,
    groupId: String,
    movingIds: Set<String>,
    name: String,
    color: Int,
): TabGroupingChange? {
    val target = groups.firstOrNull { it.id == groupId }
    val targetIds = target?.tabIdsList.orEmpty().toSet() + movingIds
    if (targetIds.size < 2 || movingIds.isEmpty() || (target != null && targetIds.size == target.tabIdsCount)) return null
    val updated = groups.mapNotNull { group ->
        if (group.id == groupId) return@mapNotNull group.toBuilder().clearTabIds().addAllTabIds(targetIds).build()
        val remaining = group.tabIdsList.filterNot { it in movingIds }
        if (remaining.size < 2) null else group.toBuilder().clearTabIds().addAllTabIds(remaining).build()
    }.toMutableList()
    if (target == null) updated += SavedTabGroup.newBuilder().setId(groupId).setName(name).addAllTabIds(targetIds).build()
    val keptIds = updated.mapTo(hashSetOf()) { it.id }
    val removedIds = groups.mapTo(hashSetOf()) { it.id } - keptIds
    val updatedColors = (colors - removedIds) + (groupId to (colors[groupId] ?: color))
    return TabGroupingChange(groupId, groups, updated, colors, updatedColors)
}
