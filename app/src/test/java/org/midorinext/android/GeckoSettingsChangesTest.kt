package org.midorinext.android

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.adblock.AdBlockConfiguration
import org.midorinext.android.adblock.BlockingLevel
import org.midorinext.android.mozac.GeckoPreferences
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.preferences.app.DoHProvider
import org.midorinext.android.preferences.app.ToolbarPosition

class GeckoSettingsChangesTest {
    private val preferences = MutableStateFlow(AppPreferencesSerializer.defaultValue)
    private val blocker = MutableStateFlow(AdBlockConfiguration())

    @Test
    fun filterUpdateTimestampsDoNotReapplyGeckoSettings() = runBlocking {
        withSettingsObserver { updates ->
            val initial = updates.receive()
            repeat(20) { timestamp ->
                blocker.value = blocker.value.copy(officialUpdatedAt = mapOf("easylist" to timestamp.toLong()))
                yield()
            }
            preferences.value = preferences.value.toBuilder()
                .setPrivacyGlobalPrivacyControl(!initial.globalPrivacyControl)
                .build()

            val changed = updates.receive()
            assertEquals(!initial.globalPrivacyControl, changed.globalPrivacyControl)
            assertTrue(updates.tryReceive().isFailure)
        }
    }

    @Test
    fun toolbarChangesDoNotReapplyGeckoSettings() = runBlocking {
        withSettingsObserver { updates ->
            val initial = updates.receive()
            preferences.value = preferences.value.toBuilder().setToolbarPosition(ToolbarPosition.BOTTOM).build()
            yield()
            preferences.value = preferences.value.toBuilder().setToolbarPosition(ToolbarPosition.TOP).build()
            yield()
            preferences.value = preferences.value.toBuilder()
                .setPrivacyFingerprintingProtection(!initial.fingerprintingProtection)
                .build()

            val changed = updates.receive()
            assertEquals(!initial.fingerprintingProtection, changed.fingerprintingProtection)
            assertTrue(updates.tryReceive().isFailure)
        }
    }

    @Test
    fun blockingLevelAndStrictnessChangesStillReachGecko() = runBlocking {
        withSettingsObserver { updates ->
            updates.receive()
            blocker.value = blocker.value.copy(level = BlockingLevel.OFF)
            assertEquals(BlockingLevel.OFF, updates.receive().adBlockLevel)

            blocker.value = blocker.value.copy(level = BlockingLevel.TRACKERS, strict = true)
            val changed = updates.receive()
            assertEquals(BlockingLevel.TRACKERS, changed.adBlockLevel)
            assertTrue(changed.strictTrackingProtection)
        }
    }

    @Test
    fun dnsAndTrackerChangesStillReachGecko() = runBlocking {
        withSettingsObserver { updates ->
            updates.receive()
            preferences.value = preferences.value.toBuilder().setDohProvider(DoHProvider.DOH_OFF).build()
            assertEquals(DoHProvider.DOH_OFF, updates.receive().dohProvider)

            blocker.value = blocker.value.copy(builtInTrackers = false)
            assertFalse(updates.receive().trackerSourceEnabled)
        }
    }

    private suspend fun withSettingsObserver(block: suspend (Channel<GeckoPreferences.UserSettings>) -> Unit) =
        kotlinx.coroutines.coroutineScope {
            val updates = Channel<GeckoPreferences.UserSettings>(Channel.UNLIMITED)
            val observer = launch(start = CoroutineStart.UNDISPATCHED) {
                geckoSettingsChanges(preferences, blocker).collect { updates.send(it) }
            }
            try {
                withTimeout(5_000) { block(updates) }
            } finally {
                observer.cancelAndJoin()
                updates.close()
            }
        }
}
