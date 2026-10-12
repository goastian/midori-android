package org.midorinext.android.ui.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    AnimatedContent(
        targetState = layout,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (fadeIn(tween(if (reduceMotion) 0 else 140, delayMillis = if (reduceMotion) 0 else 40)) togetherWith
                fadeOut(tween(if (reduceMotion) 0 else 90))).using(null)
        },
        label = "tabs layout",
    ) { targetLayout ->
        content(targetLayout)
    }
}
