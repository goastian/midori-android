package org.midorinext.android.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class BlockingStatisticsCounterTest {
    @Test
    fun networkReportsAddOnlyNewBlocksAndIgnoreReplayOrOlderBatches() {
        val counter = BlockingStatisticsCounter(10)
        counter.recordNetwork("bridge-a", 3)
        counter.recordNetwork("bridge-a", 3)
        counter.recordNetwork("bridge-a", 1)
        counter.recordNetwork("bridge-a", 5)
        assertEquals(15L, counter.totalBlocked)
    }

    @Test
    fun bridgeRestartAndNativeBlocksKeepTheAccumulatedTotal() {
        val counter = BlockingStatisticsCounter()
        counter.recordNetwork("bridge-a", 4)
        counter.record()
        counter.recordNetwork("bridge-b", 2)
        counter.recordNetwork("bridge-a", 4)
        assertEquals(7L, counter.totalBlocked)
    }

    @Test
    fun invalidReportsCannotDecreaseOrInventBlocks() {
        val counter = BlockingStatisticsCounter(9)
        counter.recordNetwork("", 100)
        counter.recordNetwork("x".repeat(129), 100)
        counter.recordNetwork("bridge", -1)
        counter.record(-10)
        assertEquals(9L, counter.totalBlocked)
    }

    @Test
    fun totalsDoNotOverflowIntoNegativeNumbers() {
        val counter = BlockingStatisticsCounter(Long.MAX_VALUE - 2)
        counter.record(3)
        assertEquals(Long.MAX_VALUE, counter.totalBlocked)
    }
}
