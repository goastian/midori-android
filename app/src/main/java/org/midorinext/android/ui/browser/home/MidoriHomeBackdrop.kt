package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun MidoriHomeBackdrop(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.055f)
    Box(modifier.drawWithCache {
        val width = size.width
        val height = size.height
        val contours = List(4) { index ->
            val inset = index * 28.dp.toPx()
            Path().apply {
                moveTo(width + inset, -height * 0.25f)
                cubicTo(width * 0.35f + inset, height * 0.12f,
                    width * 1.05f + inset, height * 0.54f, width * 0.42f + inset, height)
            }
        }
        val stroke = Stroke(width = 1.dp.toPx())
        onDrawBehind {
            if (layoutDirection == LayoutDirection.Rtl) {
                drawContext.canvas.save()
                drawContext.canvas.translate(width, 0f)
                drawContext.canvas.scale(-1f, 1f)
            }
            contours.forEach { drawPath(it, color, style = stroke) }
            if (layoutDirection == LayoutDirection.Rtl) drawContext.canvas.restore()
        }
    })
}
