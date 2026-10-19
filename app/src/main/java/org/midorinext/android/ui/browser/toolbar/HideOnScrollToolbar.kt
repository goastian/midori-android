package org.midorinext.android.ui.browser.toolbar

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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

    val dynamicToolbar = shouldHideOnScroll && !lock()
    val visible = !dynamicToolbar || toolbarState.visible || toolbarState.hasFocus
    val atBottom = toolbarPosition == ToolbarPosition.BOTTOM
    val toolbarHeightPx = with(LocalDensity.current) { ToolbarHeight.roundToPx() }
    val dynamicHeight = if (dynamicToolbar) toolbarHeightPx else 0

    DisposableEffect(engineView, dynamicHeight) {
        engineView?.setDynamicToolbarMaxHeight(dynamicHeight)
        onDispose {
            engineView?.setDynamicToolbarMaxHeight(0)
            engineView?.setVerticalClipping(0)
        }
    }
    SideEffect {
        engineView?.setVerticalClipping(if (dynamicToolbar && !visible) -dynamicHeight else 0)
    }

    // Translate a stable surface and let Gecko adjust its viewport without synchronous buffer resizes.
    val viewportModifier = if (dynamicToolbar) {
        Modifier.fillMaxSize().offset(y = if (visible && !atBottom) ToolbarHeight else 0.dp)
    } else {
        Modifier.fillMaxSize().padding(
            top = if (atBottom) 0.dp else ToolbarHeight,
            bottom = if (atBottom) ToolbarHeight else 0.dp,
        )
    }

    Box(modifier = modifier.clipToBounds()) {
        content(viewportModifier.then(contentModifier))
        if (toolbarState.hasFocus) {
            toolbar(Modifier.fillMaxSize().zIndex(2f))
        } else if (dynamicToolbar) {
            DrawAnimatedToolbar(
                visible = visible,
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
        } else {
            toolbar(
                Modifier.fillMaxWidth()
                    .align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .zIndex(2f)
            )
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

    // A fully hidden composable still participates in hit testing even with alpha = 0.
    if (visibleFraction > 0f) {
        toolbar(
            modifier.graphicsLayer {
                translationY = direction * size.height * (1f - visibleFraction)
                alpha = visibleFraction
            }
        )
    }
}
