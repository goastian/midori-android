package org.midorinext.android.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import org.midorinext.android.ui.animation.MidoriMotion

internal fun navigationEnterTransition(
    offsetPx: Int,
    reduceMotion: Boolean,
    fullScreenSlide: Boolean = false,
): EnterTransition {
    if (reduceMotion) return EnterTransition.None
    val slide = slideInHorizontally(
        tween(MidoriMotion.SurfaceDurationMillis, easing = LinearOutSlowInEasing),
        initialOffsetX = { width ->
            if (fullScreenSlide) {
                if (offsetPx < 0) -width else width
            } else offsetPx
        },
    )
    return if (fullScreenSlide) slide
    else slide + fadeIn(tween(MidoriMotion.SurfaceDurationMillis, easing = LinearOutSlowInEasing))
}

internal fun navigationExitTransition(
    offsetPx: Int,
    reduceMotion: Boolean,
    fullScreenSlide: Boolean = false,
): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    val slide = slideOutHorizontally(
        tween(MidoriMotion.SurfaceDurationMillis, easing = FastOutLinearInEasing),
        targetOffsetX = { width ->
            if (fullScreenSlide) {
                if (offsetPx < 0) -width else width
            } else offsetPx
        },
    )
    return if (fullScreenSlide) slide
    else slide + fadeOut(tween(MidoriMotion.ExitDurationMillis, easing = FastOutLinearInEasing))
}
