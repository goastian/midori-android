package org.midorinext.android.ui.animation

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

object MidoriMotion {
    const val FeedbackDurationMillis = 120
    const val SurfaceDurationMillis = 220
    const val ReplacementDurationMillis = 160
    const val ExitDurationMillis = 120
    val NavigationOffset = 32.dp
}

/** Whether spatial and decorative motion should be replaced by an instant state change. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun reduceMotionRequested(): Boolean = LocalReduceMotion.current

internal fun systemReduceMotionRequested(): Boolean = !ValueAnimator.areAnimatorsEnabled()
