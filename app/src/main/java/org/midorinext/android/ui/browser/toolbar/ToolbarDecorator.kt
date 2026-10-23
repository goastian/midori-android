package org.midorinext.android.ui.browser.toolbar

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
import org.midorinext.android.ui.widgets.MidoriIconOnBackground
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.animation.MidoriMotion

@Composable
fun ToolbarDecorator(
    state: ToolbarState,
    hintColor: Color,
    innerTextField: @Composable () -> Unit,
    trailingIcons: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    onMidoriIconClicked: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val fieldColor by animateColorAsState(
        targetValue = if (state.hasFocus) colors.surfaceContainerLowest else colors.surfaceContainer,
        animationSpec = if (LocalReduceMotion.current) snap() else tween(MidoriMotion.FeedbackDurationMillis),
        label = "address field focus",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(fieldColor)
            .border(if (state.hasFocus) 1.5.dp else 0.5.dp,
                if (state.hasFocus) colors.primary else colors.outlineVariant, shape)
    ) {
        if (leadingContent != null) {
            leadingContent()
        } else {
            when (state) {
                is BrowserToolbarState -> {
                    val shouldShowMidoriIcon = state.hasFocus || state.onMidori || state.text.text.isEmpty()
                    // Reserve one stable slot and compose only the active mode. Two independent
                    // AnimatedVisibility nodes briefly measured both icons and shifted the field.
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shouldShowMidoriIcon) {
                            MidoriIconOnBackground(
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clickable { onMidoriIconClicked() }
                            )
                        } else {
                            SiteSecurityIcon(state)
                        }
                    }
                }
                else -> MidoriIconOnBackground(
                    shape = CircleShape,
                    Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                        .clickable { onMidoriIconClicked() }
                )
            }
        }

        Box(modifier = Modifier
            .weight(2f)
            .padding(start = 8.dp)
        ) {
            // { !viewModel.toolbarState.hasFocus && currentUrl?.isNotBlank() ?: false && !(currentUrl?.isMidoriUrl() ?: false) }
            if (state.text.text.isEmpty()) {
                Text(
                    text = when (state) {
                        is BrowserToolbarState -> {
                            val currentUrl by state.currentUrl.collectAsStateWithLifecycle()
                            if (!state.onMidori && currentUrl?.isNotBlank() == true &&
                                currentUrl?.startsWith("http://") == false &&
                                currentUrl?.startsWith("https://") == false &&
                                currentUrl?.startsWith("moz-extension://") == false
                            ) {
                                currentUrl ?: ""
                            } else stringResource(if (state.hasFocus) R.string.browser_toolbar_hint else R.string.settings_search_title)
                        }
                        else -> stringResource(id = R.string.browser_toolbar_hint)
                    },
                    fontSize = 16.sp,
                    color = hintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            innerTextField()
        }

        trailingIcons()
    }
}
