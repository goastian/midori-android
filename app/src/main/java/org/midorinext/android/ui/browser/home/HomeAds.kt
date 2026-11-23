package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitCancellation
import org.midorinext.android.R
import org.midorinext.android.newtab.HomeAdDecision

@Composable
internal fun homeAdsVisibility(key: String, enabled: Boolean, viewModel: NativeHomeViewModel, position: Int? = null): Modifier {
    if (key.isEmpty()) return Modifier
    val state by viewModel.ads.collectAsStateWithLifecycle()
    val ad = state.decisions[key]
    var visible by remember(key) { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val windowInfo = LocalWindowInfo.current
    val window = windowInfo.containerSize
    val focused = windowInfo.isWindowFocused
    LaunchedEffect(key, enabled, visible, focused, ad?.decisionId, state.session, state.catalog != null, owner) {
        if (!enabled || !visible || !focused) return@LaunchedEffect
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (ad == null) viewModel.prepareHomeAd(key)
            else {
                withFrameNanos { }
                val exposure = viewModel.beginHomeAdExposure(ad, position)
                try {
                    coroutineScope {
                        launch {
                            if (viewModel.renderHomeAd(ad)) {
                                delay((1000 - exposure.visibleMillis()).coerceAtLeast(0))
                                viewModel.impressHomeAd(ad)
                            }
                        }
                        awaitCancellation()
                    }
                } finally {
                    if (!ad.isActive()) exposure.endReason = "expired"
                    viewModel.finishHomeAdExposure(exposure)
                }
            }
        }
    }
    return Modifier.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val width = (minOf(bounds.right, window.width.toFloat()) - maxOf(bounds.left, 0f)).coerceAtLeast(0f)
        val height = (minOf(bounds.bottom, window.height.toFloat()) - maxOf(bounds.top, 0f)).coerceAtLeast(0f)
        val area = coordinates.size.width.toLong() * coordinates.size.height
        visible = area > 0 && width * height / area >= 0.5f
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeAdDisclosure(
    ad: HomeAdDecision?, modifier: Modifier = Modifier, color: Color = LocalContentColor.current,
    onVisibilityChanged: (Boolean) -> Unit = {},
) {
    if (ad?.sponsorLabel == null || !ad.isActive()) return
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    val explanation = stringResource(R.string.home_ads_explanation)
    val text = remember(explanation) { AnnotatedString.fromHtml(explanation) }
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            onVisibilityChanged(true)
            try {
                awaitCancellation()
            } finally {
                onVisibilityChanged(false)
            }
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                modifier = Modifier.testTag("home_ad_privacy_tooltip"),
                caretShape = TooltipDefaults.caretShape(), maxWidth = 288.dp,
                shape = RoundedCornerShape(8.dp), containerColor = Color.White,
                contentColor = Color(0xFF30343B), shadowElevation = 4.dp,
            ) {
                Text(text, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall, fontSize = 12.sp, lineHeight = 16.sp,
                    textAlign = TextAlign.Start)
            }
        },
        state = tooltipState, modifier = modifier, focusable = true, enableUserInput = false,
    ) {
        IconButton(onClick = { scope.launch { tooltipState.show() } },
            modifier = Modifier.size(32.dp).testTag("home_ad_disclosure")) {
            Icon(painterResource(R.drawable.icons_help), stringResource(R.string.home_ads_about),
                Modifier.size(16.dp), tint = color)
        }
    }
}
