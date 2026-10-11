package org.midorinext.android

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.adblock.BlockingStatistics
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BlockingStatisticsDeviceTest {
    @Test
    fun newRepositoryRestoresPersistedTotalAndQueuedEventsAreNotLost(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "counter-fixture-${UUID.randomUUID()}"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("$prefix-$name", mode)
        }
        try {
            val statistics = BlockingStatistics(context)
            statistics.recordNetworkSnapshot("first-bridge", 3)
            statistics.recordNetworkSnapshot("first-bridge", 3)
            statistics.recordBlocked()
            withTimeout(5_000) { statistics.totalBlocked.first { it == 4L } }
            val restored = BlockingStatistics(context)
            assertEquals(4L, withTimeout(5_000) { restored.totalBlocked.first { it == 4L } })
            restored.recordNetworkSnapshot("new-bridge", 2)
            assertEquals(6L, withTimeout(5_000) { restored.totalBlocked.first { it == 6L } })
        } finally {
            app.deleteSharedPreferences("$prefix-native_blocking_statistics")
        }
    }
}
