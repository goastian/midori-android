package org.midorinext.android.ui.browser.toolbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdNestedScrollConnectionTest {

    @Test
    fun `observing a scroll never consumes the web content delta`() {
        val connection = ThresholdNestedScrollConnection(onScroll = {})
        val available = Offset(x = 14f, y = -120f)

        val consumed = connection.onPreScroll(available, NestedScrollSource.UserInput)

        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `sustained vertical scroll still notifies the toolbar`() {
        val directions = mutableListOf<Float>()
        val connection = ThresholdNestedScrollConnection(
            onScroll = directions::add,
            scrollThreshold = 0,
            consecutiveThreshold = 1,
        )

        repeat(3) {
            connection.onPreScroll(Offset(x = 0f, y = -20f), NestedScrollSource.UserInput)
        }

        assertEquals(listOf(-1f), directions)
    }
}
