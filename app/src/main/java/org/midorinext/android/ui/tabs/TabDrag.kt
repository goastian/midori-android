package org.midorinext.android.ui.tabs

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

sealed interface TabDropTarget {
    data class Tab(val id: String) : TabDropTarget
    data class Group(val id: String) : TabDropTarget
}

internal fun tabDropTarget(
    position: Offset,
    movingIds: Set<String>,
    tabs: Map<String, Rect>,
    groups: Map<String, Rect>,
    groupMembers: Map<String, Set<String>>,
): TabDropTarget? {
    fun Rect.acceptsDrop() = inflate(-minOf(width, height) * 0.12f).contains(position)
    groups.entries.firstOrNull { (id, bounds) ->
        movingIds.any { it !in groupMembers[id].orEmpty() } && bounds.acceptsDrop()
    }?.let { return TabDropTarget.Group(it.key) }
    val sourceMembers = groupMembers.values.filter { members -> movingIds.any(members::contains) }.flatten().toSet()
    return tabs.entries.firstOrNull { (id, bounds) -> id !in movingIds && id !in sourceMembers && bounds.acceptsDrop() }
        ?.let { TabDropTarget.Tab(it.key) }
}

internal class TabDragState {
    val tabBounds = mutableStateMapOf<String, Rect>()
    val groupBounds = mutableStateMapOf<String, Rect>()
    var tabId by mutableStateOf<String?>(null)
    var movingIds: Set<String> = emptySet()
    var sourceBounds: Rect? = null
    var initialPosition: Offset? = null
    var position by mutableStateOf<Offset?>(null)
    var moving by mutableStateOf(false)

    fun begin(id: String, ids: Set<String>, bounds: Rect?) {
        clear()
        tabId = id
        movingIds = ids
        sourceBounds = bounds
    }

    fun move(pointer: Offset): Boolean {
        val start = initialPosition
        if (start == null) initialPosition = pointer
        position = pointer
        val started = !moving && start != null && (pointer - start).getDistance() > 3f
        if (started) moving = true
        return started
    }

    fun clear() {
        tabId = null
        movingIds = emptySet()
        sourceBounds = null
        initialPosition = null
        position = null
        moving = false
    }
}

internal fun Modifier.tabDropBounds(state: TabDragState, id: String, group: Boolean = false): Modifier = composed {
    val bounds = if (group) state.groupBounds else state.tabBounds
    DisposableEffect(state, id, group) { onDispose { bounds.remove(id) } }
    onGloballyPositioned { bounds[id] = it.boundsInWindow() }
}

internal fun Modifier.tabDragGestures(
    id: String,
    onStart: () -> Unit,
    onPosition: (Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val start by rememberUpdatedState(onStart)
    val position by rememberUpdatedState(onPosition)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    onGloballyPositioned { coordinates = it }
        .pointerInput(id) {
            // Selection changes during the long press must not restart this pointer stream.
            detectDragGesturesAfterLongPress(
                onDragStart = { local ->
                    start()
                    coordinates?.localToWindow(local)?.let(position)
                },
                onDrag = { change, _ ->
                    change.consume()
                    coordinates?.localToWindow(change.position)?.let(position)
                },
                onDragEnd = { end() },
                onDragCancel = { cancel() },
            )
        }
}
