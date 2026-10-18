package org.midorinext.android.ui.tabs

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.midorinext.android.ui.animation.reduceMotionRequested
import kotlin.math.abs

internal fun Modifier.tabSwipeToDismiss(
    dismissal: TabDismissal,
    enabled: Boolean,
    vertical: Boolean = false,
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val reduceMotion = reduceMotionRequested()
    var resetJob by remember(dismissal) { mutableStateOf<Job?>(null) }
    pointerInput(dismissal, enabled, vertical, reduceMotion) {
        if (!enabled) return@pointerInput
        val tracker = VelocityTracker()
        var movement = 0f
        var tracking = false
        val extent = if (vertical) size.height else size.width
        val threshold = minOf(extent * 0.3f, 96.dp.toPx())
        fun restore() {
            resetJob?.cancel()
            if (reduceMotion) {
                dismissal.dragOffset = 0f
                return
            }
            resetJob = scope.launch {
                animate(dismissal.dragOffset, 0f, animationSpec = tween(160)) { value, _ ->
                    dismissal.dragOffset = value
                }
            }
        }
        val drag: (PointerInputChange, Float) -> Unit = { change, amount ->
            change.consume()
            if (!tracking) tracker.addPosition(change.previousUptimeMillis, Offset.Zero)
            tracking = true
            movement += amount
            tracker.addPosition(change.uptimeMillis, if (vertical) Offset(0f, movement) else Offset(movement, 0f))
            dismissal.dragOffset = (dismissal.dragOffset + amount).let { if (vertical) it.coerceAtMost(0f) else it }
        }
        val finish = {
            val velocity = tracker.calculateVelocity().let { if (vertical) it.y else it.x }
            val flung = abs(dismissal.dragOffset) >= 24.dp.toPx() && abs(velocity) >= 900.dp.toPx() &&
                velocity * dismissal.dragOffset > 0f
            if (abs(dismissal.dragOffset) >= threshold || flung) dismissal.dismiss() else restore()
        }
        if (vertical) {
            detectVerticalDragGestures(
                onDragStart = { resetJob?.cancel(); tracker.resetTracking(); movement = 0f; tracking = false },
                onDragEnd = finish,
                onDragCancel = ::restore,
                onVerticalDrag = drag,
            )
        } else {
            detectHorizontalDragGestures(
                onDragStart = { resetJob?.cancel(); tracker.resetTracking(); movement = 0f; tracking = false },
                onDragEnd = finish,
                onDragCancel = ::restore,
                onHorizontalDrag = drag,
            )
        }
    }
}
