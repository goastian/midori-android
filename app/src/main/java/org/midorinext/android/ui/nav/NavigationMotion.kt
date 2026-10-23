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

internal fun navigationEnterTransition(offsetPx: Int, reduceMotion: Boolean): EnterTransition =
    if (reduceMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(MidoriMotion.SurfaceDurationMillis, easing = LinearOutSlowInEasing)) +
            slideInHorizontally(
                tween(MidoriMotion.SurfaceDurationMillis, easing = LinearOutSlowInEasing),
                initialOffsetX = { offsetPx },
            )
    }

internal fun navigationExitTransition(offsetPx: Int, reduceMotion: Boolean): ExitTransition =
    if (reduceMotion) {
        ExitTransition.None
    } else {
        fadeOut(tween(MidoriMotion.ExitDurationMillis, easing = FastOutLinearInEasing)) +
            slideOutHorizontally(
                tween(MidoriMotion.SurfaceDurationMillis, easing = FastOutLinearInEasing),
                targetOffsetX = { offsetPx },
            )
    }
