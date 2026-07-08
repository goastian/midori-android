package org.midorinext.android.ext

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Firefox 146+ Feature: Gesture-based Tab Switching
 *
 * Provides swipe gestures to switch between tabs:
 * - Swipe left: Next tab
 * - Swipe right: Previous tab
 */
class TabSwitchGestureListener(
    private val onSwipeLeft: () -> Unit = {},
    private val onSwipeRight: () -> Unit = {},
    private val onDoubleTap: () -> Unit = {},
    private val onLongPress: () -> Unit = {}
) : GestureDetector.SimpleOnGestureListener() {

    companion object {
        // Minimum swipe distance in pixels
        private const val SWIPE_DISTANCE_THRESHOLD = 100
        // Maximum swipe velocity
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    // API 34+: e2 is non-nullable
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false

        val distanceX = e2.x - e1.x
        val distanceY = e2.y - e1.y

        // Only handle horizontal swipes
        if (abs(distanceY) > abs(distanceX)) return false
        if (abs(distanceX) < SWIPE_DISTANCE_THRESHOLD) return false
        if (abs(velocityX) < SWIPE_VELOCITY_THRESHOLD) return false

        return when {
            distanceX < 0 -> { onSwipeLeft(); true }
            distanceX > 0 -> { onSwipeRight(); true }
            else -> false
        }
    }

    // API 34+: e is non-nullable
    override fun onDoubleTap(e: MotionEvent): Boolean {
        onDoubleTap()
        return true
    }

    // API 34+: e is non-nullable
    override fun onLongPress(e: MotionEvent) {
        super.onLongPress(e)
        onLongPress()
    }
}

/**
 * Manager for tab switching gestures
 */
class TabGestureManager(context: Context) {
    val gestureDetector: GestureDetector

    var onSwipeLeft: () -> Unit = {}
    var onSwipeRight: () -> Unit = {}
    var onDoubleTap: () -> Unit = {}
    var onLongPress: () -> Unit = {}

    init {
        gestureDetector = GestureDetector(
            context,
            TabSwitchGestureListener(
                onSwipeLeft = { onSwipeLeft() },
                onSwipeRight = { onSwipeRight() },
                onDoubleTap = { onDoubleTap() },
                onLongPress = { onLongPress() }
            )
        )
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }
}

/**
 * Extension function to create a tab gesture manager
 */
fun Context.createTabGestureManager(): TabGestureManager {
    return TabGestureManager(this)
}