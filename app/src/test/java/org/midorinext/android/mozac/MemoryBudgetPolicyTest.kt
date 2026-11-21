package org.midorinext.android.mozac

import android.content.ComponentCallbacks2
import org.junit.Assert.*
import org.junit.Test

class MemoryBudgetPolicyTest {
    @Test fun profilesRespectPhysicalRamLowRamAndJavaClassWithoutTreatingJavaAsGeckoMemory() {
        assertEquals(3, MemoryProfile.forDevice(true, 12000, 512).foreground)
        assertEquals(3, MemoryProfile.forDevice(false, 2048, 512).foreground)
        assertEquals(3, MemoryProfile.forDevice(false, 12000, 128).foreground)
        assertEquals(4, MemoryProfile.forDevice(false, 4096, 512).foreground)
        assertEquals(4, MemoryProfile.forDevice(false, 12000, 256).foreground)
        assertEquals(5, MemoryProfile.forDevice(false, 12000, 512).foreground)
    }

    @Test fun modernVisibilitySignalsNeedNoDeprecatedPressureNotifications() {
        for (level in listOf(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)) {
            val policy = MemoryBudgetPolicy(MemoryProfile("test", 5, 2)) { 0 }
            policy.trim(level)
            assertEquals(2, policy.limit)
            policy.visibility(true)
            assertEquals(5, policy.limit)
            policy.visibility(false)
            assertEquals(2, policy.limit)
            assertTrue(shouldClearBrowserMemory(level))
        }
    }

    @Test fun onlyAnActualBackgroundTransitionCanReleaseTheIdleSelectionOnLowMemoryDevices() {
        val policy = MemoryBudgetPolicy(MemoryProfile.forDevice(false, 3804, 256)) { 0 }
        assertEquals(0, policy.profile.background)
        assertEquals(1, policy.limit)
        assertFalse(policy.canSuspendIdleSelection)

        policy.visibility(true)
        policy.trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertEquals(1, policy.limit)
        assertFalse(policy.canSuspendIdleSelection)

        policy.visibility(false)
        assertEquals(0, policy.limit)
        assertTrue(policy.canSuspendIdleSelection)
        policy.visibility(true)
        assertEquals(policy.profile.foreground, policy.limit)
        assertFalse(policy.canSuspendIdleSelection)
        assertEquals(1, MemoryProfile.forDevice(false, 8192, 256).background)
    }

    @Test fun repeatedPressureExtendsCooldownAndResumeDoesNotImmediatelyRaiseTheLimit() {
        var time = 0L
        val policy = MemoryBudgetPolicy(MemoryProfile("test", 5, 2)) { time }
        policy.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        policy.visibility(false)
        policy.visibility(true)
        assertEquals(2, policy.limit)
        time = 29_000
        policy.trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        time = 30_001
        assertEquals(2, policy.limit)
        time = 59_000
        assertEquals(5, policy.limit)
        policy.visibility(false)
        assertEquals(2, policy.limit)
        assertFalse(shouldClearBrowserMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE))
    }
}
