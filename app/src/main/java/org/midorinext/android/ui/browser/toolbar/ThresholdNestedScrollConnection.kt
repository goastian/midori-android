package org.midorinext.android.ui.browser.toolbar

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.sign


class ThresholdNestedScrollConnection(
    private val onScroll: (sign: Float) -> Unit,
    private val scrollThreshold: Int = 10,
    private val consecutiveThreshold: Int = 4
) : NestedScrollConnection {
    private var consecutiveScroll = 0
    private var lastOffsetSign = 0f

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y.sign == lastOffsetSign && (available.y > scrollThreshold || available.y < -scrollThreshold)) {
            consecutiveScroll++
        } else {
            consecutiveScroll = 0
        }
        if (consecutiveScroll > consecutiveThreshold) {
            onScroll(available.y.sign)
        }
        lastOffsetSign = available.y.sign

        return available // Offset.Zero // available ?
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // TODO handle fling there too ? never seems to be called
        Log.d("MIDORI_SCROLL", "fling $available")
        return super.onPreFling(available)
    }
}

@Composable
fun rememberThresholdNestedScrollConnection(
    onScroll: (sign: Float) -> Unit,
    scrollThreshold: Int = 10,
    consecutiveThreshold: Int = 4
) : ThresholdNestedScrollConnection {
    return remember(onScroll, scrollThreshold, consecutiveThreshold) {
        ThresholdNestedScrollConnection(onScroll, scrollThreshold, consecutiveThreshold)
    }
}