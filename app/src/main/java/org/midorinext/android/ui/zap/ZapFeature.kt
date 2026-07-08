package org.midorinext.android.ui.zap

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.midorinext.android.ui.widgets.YesNoDialog
import org.midorinext.android.R
import org.midorinext.android.ui.theme.Grey900
import org.midorinext.android.ui.theme.ZapYellow

@Composable
fun ZapFeature(
    state: ZapState
) {
    ZapAnimation(state)

    when (state.requestStatus) {
        ZapState.RequestStatus.Confirm -> ZapConfirmDialog(state)
        ZapState.RequestStatus.Error -> ZapErrorDialog(state)
        else -> {}
    }
}

@Composable
internal fun ZapAnimation(
    state: ZapState
) {
    // TODO use updateTransition to group zap animations

    val stepDuration = 200
    val easing = EaseIn

    var showBackground by remember { mutableStateOf(false) }
    var showIcon by remember { mutableStateOf(false) }
    var showBlackOverlay  by remember { mutableStateOf(false) }
    var exitBlackOverlay  by remember { mutableStateOf(false) }

    LaunchedEffect(state.animationStatus) {
        if (state.animationStatus == ZapState.AnimationStatus.In) {
            showBackground = true
        }
        if (state.animationStatus == ZapState.AnimationStatus.Out) {
            showIcon = false
        }
    }

    val background by animateColorAsState(
        targetValue = if (showBackground) ZapYellow else ZapYellow.copy(0f),
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapYellowBackground",
        finishedListener = {
            if (showBackground) {
                showIcon = true
            } else {
                state.updateAnimationState(ZapState.AnimationStatus.Idle)
            }
        }
    )

    val iconAlpha by animateFloatAsState(
        targetValue = if (showIcon) 1f else 0f,
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapIconAlpha"
    )
    val iconOffsetX by animateDpAsState(
        targetValue = if (showIcon) 0.dp else 50.dp,
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapIconOffsetX"
    )
    val iconOffsetY by animateDpAsState(
        targetValue = if (showIcon) 0.dp else 100.dp,
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapIconOffsetY",
        finishedListener = {
            if (showIcon) {
                showBlackOverlay = true
            } else {
                showBackground = false
            }
        }
    )

    val blackOverlayColor by animateColorAsState(
        targetValue = if (showBlackOverlay && !exitBlackOverlay) Grey900 else Grey900.copy(0f),
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapBlackOverlayAlpha",
        finishedListener = {
            if (exitBlackOverlay) {
                showBlackOverlay = false
                exitBlackOverlay = false
                state.updateAnimationState(ZapState.AnimationStatus.Wait)
            } else {
                exitBlackOverlay = true
            }
        }
    )
    val blackOverlayMaxSizeDp = maxOf(LocalConfiguration.current.screenHeightDp, LocalConfiguration.current.screenWidthDp)
    val blackOverlaySize by animateDpAsState(
        targetValue = if (showBlackOverlay) blackOverlayMaxSizeDp.dp else 0.dp,
        animationSpec = tween(durationMillis = stepDuration, easing = easing),
        label = "ZapBlackOverlaySize"
    )
    val blackOverlayCornerPercent by animateIntAsState(
        targetValue = if (showBlackOverlay) 0 else 50,
        animationSpec = keyframes {
            durationMillis = stepDuration
            50.at(0)
            50.at(stepDuration * 9 / 10)
            0.at(stepDuration)
        },
        label = "blackOverlayCornerPercent"
    )

    val infiniteRotation = if (state.animationStatus == ZapState.AnimationStatus.Wait) {
        rememberInfiniteTransition(label = "ZapInfiniteTransition").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 400, delayMillis = 800, easing = EaseInOut)
            ),
            label = "ZapInfiniteRotation"
        )
    } else remember { mutableFloatStateOf(0f) }

    if (state.animationStatus != ZapState.AnimationStatus.Idle) {
        // TODO ? replace animation dialog with surface, but we need first to modify fullscreen preferences (which are also dialogs ...)
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            ),
            onDismissRequest = {},
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(background)
                    .rotate(infiniteRotation.value)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zap_top_full),
                    contentDescription = "zap top",
                    contentScale = ContentScale.None,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = iconAlpha)
                        .offset(iconOffsetX, -iconOffsetY)
                )
                Image(
                    painter = painterResource(id = R.drawable.zap_bottom_full),
                    contentDescription = "zap bottom",
                    contentScale = ContentScale.None,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = iconAlpha)
                        .offset(-iconOffsetX, iconOffsetY)
                )
                Box(modifier = Modifier
                    .requiredSize(blackOverlaySize)
                    .clip(RoundedCornerShape(percent = blackOverlayCornerPercent))
                    .background(blackOverlayColor)
                )
            }
        }
    }
}

@Composable
internal fun ZapConfirmDialog(state: ZapState) {
    YesNoDialog(
        onDismissRequest = { state.consumeZapRequest(false) },
        onYes = { state.consumeZapRequest(true) },
        onNo = { state.consumeZapRequest(false) },
        description = stringResource(id = R.string.cleardata_confirm_text),
        yesText = stringResource(id = R.string.erase)
    )
}

@Composable
internal fun ZapErrorDialog(state: ZapState) {
    YesNoDialog(
        onDismissRequest = { state.consumeZapError() },
        onYes = { state.consumeZapRequest(true) },
        onNo = { state.consumeZapError() },
        description = stringResource(id = R.string.cleardata_failed),
        yesText = stringResource(id = R.string.try_again)
    )
}
