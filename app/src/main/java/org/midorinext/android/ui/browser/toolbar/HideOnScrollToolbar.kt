package org.midorinext.android.ui.browser.toolbar

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import org.midorinext.android.preferences.app.ToolbarPosition
import org.midorinext.android.ui.animation.reduceMotionRequested
import mozilla.components.concept.engine.EngineView

@Composable
fun HideOnScrollToolbar(
    toolbarState: BrowserToolbarState,
    toolbar: @Composable (Modifier) -> Unit,
    engineView: EngineView?,
    modifier: Modifier = Modifier,
    lock: () -> Boolean = { false },
    content: @Composable (Modifier) -> Unit = {},
) {
    val toolbarPosition by toolbarState.toolbarPosition.collectAsStateWithLifecycle()

    val shouldHideOnScroll by toolbarState.shouldHideOnScroll.collectAsStateWithLifecycle()

    val nestedScrollConnection = rememberThresholdNestedScrollConnection(
        onScroll = { sign ->
            if (engineView?.canScrollVerticallyUp() == false) {
                toolbarState.updateVisibility(true)
            } else {
                toolbarState.updateVisibility(sign == 1f)
            }
        },
        scrollThreshold = 5, // if (position == HideOnScrollPosition.Top) 5 else 1,
        consecutiveThreshold = 4 // if (position == HideOnScrollPosition.Top) 4 else 1
    )

    val contentModifier = if (!lock() && shouldHideOnScroll) {
        Modifier.nestedScroll(nestedScrollConnection)
    } else Modifier

    if (toolbarState.hasFocus) {
        // Keep GeckoView composed while the Firefox-style edit surface replaces it visually.
        Box(modifier = modifier) {
            content(Modifier.fillMaxSize())
            toolbar(
                Modifier
                    .fillMaxSize()
                    .zIndex(2f)
            )
        }
    } else if (shouldHideOnScroll) {
        // In scroll-aware mode the browser surface owns its final size from the start. Moving the
        // toolbar in draw avoids remeasuring GeckoView and the rest of the screen on every frame.
        Box(modifier = modifier) {
            content(
                Modifier
                    .fillMaxSize()
                    .then(contentModifier)
            )
            DrawAnimatedToolbar(
                visible = toolbarState.visible,
                toolbarPosition = toolbarPosition,
                toolbar = toolbar,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(
                        if (toolbarPosition == ToolbarPosition.BOTTOM) {
                            Alignment.BottomCenter
                        } else {
                            Alignment.TopCenter
                        }
                    )
                    .zIndex(2f),
            )
        }
    } else {
        Column(modifier = modifier) {
            if (toolbarPosition == ToolbarPosition.BOTTOM) {
                content(
                    Modifier
                        .fillMaxWidth()
                        .weight(2f, true)
                        .then(contentModifier)
                )
                toolbar(Modifier.fillMaxWidth().zIndex(2f))
            } else if (toolbarPosition == ToolbarPosition.TOP) {
                toolbar(Modifier.fillMaxWidth().zIndex(2f))
                content(
                    Modifier
                        .fillMaxWidth()
                        .weight(2f, true)
                        .then(contentModifier)
                )
            }
        }
    }
}

@Composable
private fun DrawAnimatedToolbar(
    visible: Boolean,
    toolbarPosition: ToolbarPosition,
    toolbar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = reduceMotionRequested()
    val visibleFraction by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(durationMillis = 120),
        label = "scrollToolbarVisibility",
    )
    val direction = if (toolbarPosition == ToolbarPosition.BOTTOM) 1f else -1f

    toolbar(
        modifier.graphicsLayer {
            translationY = direction * size.height * (1f - visibleFraction)
            alpha = visibleFraction
        }
    )
}
