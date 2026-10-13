package org.midorinext.android.ui.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.midorinext.android.ui.animation.reduceMotionRequested

internal class TabDismissal {
    val progress = Animatable(0f)
    var requested by mutableStateOf(false)
        private set
    private var committed = false

    fun dismiss() {
        requested = true
    }

    fun commit(onDismiss: () -> Unit) {
        if (requested && !committed) {
            committed = true
            onDismiss()
        }
    }
}

@Composable
internal fun rememberTabDismissal(tabId: String, onDismiss: () -> Unit): TabDismissal {
    val dismissal = remember(tabId) { TabDismissal() }
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val reduceMotion = reduceMotionRequested()
    DisposableEffect(dismissal) {
        onDispose { dismissal.commit(currentOnDismiss) }
    }
    LaunchedEffect(dismissal.requested) {
        if (dismissal.requested) {
            if (reduceMotion) dismissal.progress.snapTo(1f)
            else dismissal.progress.animateTo(1f, tween(160, easing = FastOutLinearInEasing))
            dismissal.commit(currentOnDismiss)
        }
    }
    return dismissal
}
