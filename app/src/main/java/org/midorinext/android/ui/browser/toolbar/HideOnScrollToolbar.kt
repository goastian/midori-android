package org.midorinext.android.ui.browser.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.midorinext.android.preferences.app.ToolbarPosition
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
    val toolbarPosition by toolbarState.toolbarPosition.collectAsState()

    val shouldHideOnScroll = false // by toolbarState.shouldHideOnScroll.collectAsState()

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

    Column(modifier = modifier) {
        if (toolbarPosition == ToolbarPosition.BOTTOM) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
            content(
                Modifier
                    .fillMaxWidth()
                    .weight(2f, true)
                    .then(contentModifier)
            )
            AnimatedToolbar(
                toolbarState,
                toolbar,
                Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
            )
        } else if (toolbarPosition == ToolbarPosition.TOP) {
            AnimatedToolbar(
                toolbarState,
                toolbar,
                Modifier
                    .fillMaxWidth()
                    .zIndex(2f)
            )
            content(
                Modifier
                    .fillMaxWidth()
                    .weight(2f, true)
                    .then(contentModifier)
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun AnimatedToolbar(
    toolbarState: BrowserToolbarState,
    toolbar: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldHideOnScroll = false // by toolbarState.shouldHideOnScroll.collectAsState()

    if (shouldHideOnScroll) {
        if (!toolbarState.visible) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        AnimatedVisibility(
            visible = toolbarState.visible,
            enter = expandVertically(animationSpec = tween(100)),
            exit = shrinkVertically(animationSpec = tween(100))
        ) {
            toolbar(modifier)
        }
    } else {
        toolbar(modifier)
    }
}
