package org.midorinext.android

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.midorinext.android.diagnostics.ScenarioMetrics

class MemoryVisibilityDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun homeAndReturnDriveTheBudgetThroughActualActivityVisibility() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val policy = app.memoryBudget.policy
        fun memoryChecks(): Long {
            var checks = 0L
            instrumentation.runOnMainSync { checks = ScenarioMetrics.snapshot().getLong("memory_checked") }
            return checks
        }
        fun awaitVisible(expected: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (true) {
                var visible = !expected
                instrumentation.runOnMainSync { visible = policy.visible }
                if (visible == expected) return
                check(SystemClock.elapsedRealtime() < deadline) { "Visibility did not become $expected" }
                SystemClock.sleep(100)
            }
        }
        awaitVisible(true)
        ScenarioMetrics.start()
        try {
            val deadline = SystemClock.elapsedRealtime() + 40_000
            while (memoryChecks() < 2) {
                check(SystemClock.elapsedRealtime() < deadline) { "Foreground memory checks did not continue" }
                SystemClock.sleep(100)
            }
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            awaitVisible(false)
            assertEquals(app.memoryBudget.profile.background, policy.limit)
            SystemClock.sleep(1_000)
            val backgroundChecks = memoryChecks()
            SystemClock.sleep(16_000)
            assertEquals(backgroundChecks, memoryChecks())
        } finally {
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
            awaitVisible(true)
            val previous = memoryChecks()
            val deadline = SystemClock.elapsedRealtime() + 20_000
            try {
                while (memoryChecks() <= previous) {
                    check(SystemClock.elapsedRealtime() < deadline) { "Memory checks did not restart after returning" }
                    SystemClock.sleep(100)
                }
            } finally {
                ScenarioMetrics.enabled = false
            }
        }
    }
}
