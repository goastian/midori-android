package org.midorinext.android.ui.browser.pullToRefresh

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FloatTweenSpec
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.TimeSource

@OptIn(ExperimentalMaterial3Api::class)
interface CustomPullToRefreshState: PullToRefreshState {
    fun addOffset(offset: Float)
    fun resetOffset()
    fun animateOffset(offset: Float, duration: Int)
}

@Composable
fun rememberPullToRefreshState(): CustomPullToRefreshState {
    return remember {
        object: CustomPullToRefreshState {
            private var coroutineScope = MainScope()

            val positionalThreshold: Float = 250f
            var verticalOffset: Float by mutableFloatStateOf(0f)

            override var isAnimating: Boolean = false

            override val distanceFraction: Float
                get() = verticalOffset / positionalThreshold

            override fun addOffset(offset: Float) {
                verticalOffset = (verticalOffset + offset).coerceIn(0f, positionalThreshold)
            }

            override fun resetOffset() {
                verticalOffset = 0f
            }

            override fun animateOffset(offset: Float, duration: Int) {
                coroutineScope.launch {
                    isAnimating = true
                    val tween = FloatTweenSpec(duration, easing = FastOutLinearInEasing)
                    val initialValue = verticalOffset
                    val startMark = TimeSource.Monotonic.markNow()
                    while (abs(verticalOffset - offset) > 0.5f) {
                        val ns = startMark.elapsedNow().inWholeNanoseconds
                        verticalOffset = tween.getValueFromNanos(ns, initialValue, offset, 0f)
                        delay(10)
                    }
                    verticalOffset = offset
                    isAnimating = false
                }
            }

            override suspend fun animateToHidden() {}
            override suspend fun animateToThreshold() {}
            override suspend fun snapTo(targetValue: Float) {}
        }
    }
}
