package org.midorinext.android.ui.browser.home

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import org.midorinext.android.ui.animation.LocalReduceMotion
import org.midorinext.android.ui.animation.MidoriMotion
import kotlin.random.Random

internal fun Modifier.homeGrain(): Modifier = drawWithCache {
    val random = Random(41)
    val pixels = IntArray(128 * 128) {
        if (random.nextBoolean()) 0x05000000 else 0x05FFFFFF
    }
    val texture = Bitmap.createBitmap(pixels, 128, 128, Bitmap.Config.ARGB_8888).asImageBitmap()
    val brush = ShaderBrush(ImageShader(texture, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(brush) }
}

@Composable
internal fun Modifier.homePressFeedback(interactions: MutableInteractionSource): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.97f else 1f,
        animationSpec = if (reduceMotion) snap() else tween(
            durationMillis = if (pressed) 80 else MidoriMotion.SurfaceDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "home press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}
