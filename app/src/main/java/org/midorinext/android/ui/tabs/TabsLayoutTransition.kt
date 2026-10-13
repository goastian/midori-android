package org.midorinext.android.ui.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.midorinext.android.preferences.app.TabsViewOption
import org.midorinext.android.ui.animation.reduceMotionRequested

@Composable
internal fun TabsLayoutTransition(
    layout: TabsViewOption,
    content: @Composable (TabsViewOption) -> Unit,
) {
    val reduceMotion = reduceMotionRequested()
    if (reduceMotion) {
        content(layout)
        return
    }
    AnimatedContent(
        targetState = layout,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ((fadeIn(tween(180, delayMillis = 20)) + scaleIn(tween(200), initialScale = 0.96f)) togetherWith
                (fadeOut(tween(100)) + scaleOut(tween(160), targetScale = 1.02f))).using(null)
        },
        label = "tabs layout",
    ) { targetLayout ->
        content(targetLayout)
    }
}
