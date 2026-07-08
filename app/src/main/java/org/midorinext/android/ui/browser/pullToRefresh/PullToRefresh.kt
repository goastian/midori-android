package org.midorinext.android.ui.browser.pullToRefresh

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefreshBox(
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: () -> Boolean = { true },
    content: @Composable BoxScope.() -> Unit
) {
    var pullToRefreshRunning by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    Box(modifier.pointerInput(true) {
        detectVerticalDragGesturesUnconsumed(
            onDragStart = {},
            onDragEnd = {
                if (pullRefreshState.distanceFraction > 0.95f) {
                    onRefresh()
                }
                pullToRefreshRunning = false
                pullRefreshState.animateOffset(0f, 200)
            },
            onDragCancel = {
                pullToRefreshRunning = false
                pullRefreshState.animateOffset(0f, 200)
            },
            onVerticalDrag = { change, dragAmount ->
                if (dragAmount > 0 && (pullToRefreshRunning || enabled())) {
                    pullRefreshState.addOffset(dragAmount)
                    pullToRefreshRunning = true
                    change.consume()
                } else if (dragAmount < 0 && pullToRefreshRunning) {
                    pullRefreshState.addOffset(dragAmount)
                    if (pullRefreshState.distanceFraction == 0f) {
                        pullToRefreshRunning = false
                    }
                    change.consume()
                }
            }
        )
    }) {
        content()

        Box(modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = (-5).dp),
        ) {
            PullToRefreshDefaults.Indicator(
                state = pullRefreshState,
                isRefreshing = pullToRefreshRunning,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
