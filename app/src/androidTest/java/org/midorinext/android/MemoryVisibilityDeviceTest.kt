package org.midorinext.android

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MemoryVisibilityDeviceTest {
    @get:Rule val activity = createAndroidComposeRule<MainActivity>()

    @Test fun homeAndReturnDriveTheBudgetThroughActualActivityVisibility() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as MidoriApplication
        val policy = app.memoryBudget.policy
        val visibleField = policy.javaClass.getDeclaredField("visible").apply { isAccessible = true }
        fun awaitVisible(expected: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (true) {
                var visible = !expected
                instrumentation.runOnMainSync { visible = visibleField.getBoolean(policy) }
                if (visible == expected) return
                check(SystemClock.elapsedRealtime() < deadline) { "Visibility did not become $expected" }
                SystemClock.sleep(100)
            }
        }
        awaitVisible(true)
        try {
            instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            awaitVisible(false)
            assertEquals(app.memoryBudget.profile.background, policy.limit)
        } finally {
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            })
            awaitVisible(true)
        }
    }
}
