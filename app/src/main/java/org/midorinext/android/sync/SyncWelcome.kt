package org.midorinext.android.sync

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R
import org.midorinext.android.ui.animation.LocalReduceMotion

@Composable
internal fun SyncWelcome(
    busy: Boolean,
    error: String?,
    onScan: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.sync_scan_title),
                style = MaterialTheme.typography.displaySmall.copy(lineHeight = 36.sp),
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sync_scan_summary), style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant)
            DeviceConnection(Modifier.fillMaxWidth().padding(vertical = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(3.dp).height(72.dp).background(colors.primary, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(24.dp))
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 16.dp))
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error,
                    modifier = Modifier.padding(bottom = 16.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = onScan, enabled = !busy, shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                ScanFrame(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.sync_scan), style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onSignIn, enabled = !busy, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.sync_sign_in_authentik), style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.icons_arrow_forward), contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.icons_lock), contentDescription = null,
                    tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.sync_encrypted_transfer), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ScanFrame(modifier: Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.clearAndSetSemantics { }) {
        val path = Path().apply {
            moveTo(0.36f, 0.12f); lineTo(0.12f, 0.12f); lineTo(0.12f, 0.36f)
            moveTo(0.64f, 0.12f); lineTo(0.88f, 0.12f); lineTo(0.88f, 0.36f)
            moveTo(0.12f, 0.64f); lineTo(0.12f, 0.88f); lineTo(0.36f, 0.88f)
            moveTo(0.64f, 0.88f); lineTo(0.88f, 0.88f); lineTo(0.88f, 0.64f)
            moveTo(0.32f, 0.5f); lineTo(0.68f, 0.5f)
        }
        withTransform({ scale(size.width, size.height, pivot = Offset.Zero) }) {
            drawPath(path, color, style = Stroke(width = 0.075f, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun DeviceConnection(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalReduceMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val entrance by animateFloatAsState(if (visible) 1f else 0f,
        animationSpec = tween(if (reduceMotion) 0 else 420), label = "deviceConnection")
    Canvas(modifier.aspectRatio(320f / 184f).clearAndSetSemantics { }.graphicsLayer {
        alpha = entrance
        translationY = (1f - entrance) * 8.dp.toPx()
    }) {
        withTransform({ scale(size.width / 320f, size.width / 320f, pivot = Offset.Zero) }) {
            val line = Stroke(1.5f)
            val orbit = Path().apply {
                moveTo(58f, 36f)
                cubicTo(98f, -4f, 209f, -12f, 270f, 37f)
            }
            drawPath(orbit, colors.outlineVariant, style = line)
            drawCircle(colors.primary, 3f, Offset(270f, 37f))

            drawRoundRect(colors.surfaceContainerLow, Offset(12f, 36f), Size(240f, 126f), CornerRadius(10f))
            drawRoundRect(colors.outlineVariant, Offset(12f, 36f), Size(240f, 126f), CornerRadius(10f), style = line)
            drawLine(colors.outlineVariant, Offset(12f, 59f), Offset(252f, 59f), 1f)
            for (x in listOf(25f, 33f, 41f)) drawCircle(colors.outline, 1.8f, Offset(x, 48f))
            drawRoundRect(colors.surfaceContainerHigh, Offset(67f, 44f), Size(139f, 8f), CornerRadius(4f))
            drawLine(colors.outlineVariant, Offset(61f, 59f), Offset(61f, 162f), 1f)
            drawRoundRect(colors.primaryContainer, Offset(22f, 71f), Size(29f, 18f), CornerRadius(4f))
            for (y in listOf(101f, 115f, 129f)) {
                drawLine(colors.outlineVariant, Offset(28f, y), Offset(46f, y), 3f, StrokeCap.Round)
            }

            drawRoundRect(colors.primaryContainer, Offset(75f, 71f), Size(160f, 32f), CornerRadius(6f))
            drawCircle(colors.primary, 6f, Offset(91f, 87f))
            drawLine(colors.primary, Offset(107f, 83f), Offset(171f, 83f), 3f, StrokeCap.Round)
            drawLine(colors.primary.copy(alpha = 0.4f), Offset(107f, 91f), Offset(147f, 91f), 3f, StrokeCap.Round)
            for (x in listOf(75f, 130f, 185f)) {
                drawRoundRect(colors.surface, Offset(x, 113f), Size(48f, 34f), CornerRadius(5f))
                drawRoundRect(colors.outlineVariant, Offset(x, 113f), Size(48f, 34f), CornerRadius(5f), style = Stroke(1f))
                drawLine(colors.outlineVariant, Offset(x + 10f, 125f), Offset(x + 28f, 125f), 3f, StrokeCap.Round)
                drawLine(colors.outlineVariant, Offset(x + 10f, 135f), Offset(x + 36f, 135f), 2f, StrokeCap.Round)
            }
            val base = Path().apply {
                moveTo(3f, 163f); lineTo(261f, 163f)
                quadraticTo(256f, 170f, 246f, 170f)
                lineTo(18f, 170f); quadraticTo(8f, 170f, 3f, 163f)
                close()
            }
            drawPath(base, colors.surfaceContainerHigh)
            drawPath(base, colors.outlineVariant, style = line)

            drawRoundRect(colors.surface, Offset(228f, 63f), Size(78f, 120f), CornerRadius(13f))
            drawRoundRect(colors.onSurface, Offset(228f, 63f), Size(78f, 120f), CornerRadius(13f), style = Stroke(2f))
            drawLine(colors.onSurface, Offset(258f, 70f), Offset(276f, 70f), 2.5f, StrokeCap.Round)
            drawRoundRect(colors.primaryContainer, Offset(237f, 84f), Size(60f, 37f), CornerRadius(5f))
            drawCircle(colors.primary, 6f, Offset(251f, 98f))
            drawLine(colors.primary, Offset(264f, 95f), Offset(285f, 95f), 2.5f, StrokeCap.Round)
            drawLine(colors.primary.copy(alpha = 0.4f), Offset(246f, 111f), Offset(280f, 111f), 2.5f, StrokeCap.Round)
            for (y in listOf(133f, 147f, 161f)) {
                drawCircle(colors.outlineVariant, 3f, Offset(242f, y))
                drawLine(colors.outlineVariant, Offset(253f, y), Offset(287f, y), 3f, StrokeCap.Round)
            }
            drawLine(colors.onSurface, Offset(258f, 176f), Offset(276f, 176f), 2f, StrokeCap.Round)

            drawRoundRect(colors.primary, Offset(189f, 9f), Size(40f, 40f), CornerRadius(12f))
            val arrows = Path().apply {
                moveTo(198f, 26f); lineTo(218f, 26f); lineTo(213f, 21f)
                moveTo(218f, 32f); lineTo(198f, 32f); lineTo(203f, 37f)
            }
            drawPath(arrows, colors.onPrimary, style = Stroke(2f, cap = StrokeCap.Round))
        }
    }
}
