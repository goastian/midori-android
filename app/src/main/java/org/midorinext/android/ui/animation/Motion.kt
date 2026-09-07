package org.midorinext.android.ui.animation

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Whether spatial and decorative motion should be replaced by an instant state change. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun reduceMotionRequested(): Boolean = LocalReduceMotion.current

internal fun systemReduceMotionRequested(): Boolean = !ValueAnimator.areAnimatorsEnabled()
