package org.midorinext.android.mozac

import java.lang.reflect.Proxy
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.concept.engine.media.RecordingDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabResourcePolicyTest {
    @Test
    fun suspendsLeastRecentlyUsedTabsAndKeepsTheSelectedTab() {
        val tabs = listOf(
            ResidentTab("selected", 0, true),
            ResidentTab("recent", 30, false),
            ResidentTab("oldest", 10, false),
            ResidentTab("older", 20, false),
        )
        assertEquals(listOf("oldest", "older"), tabsToSuspend(tabs, 2))
    }

    @Test
    fun protectedTabsCanExceedTheBudgetWithoutLosingUserData() {
        val tabs = listOf(
            ResidentTab("selected", 30, true),
            ResidentTab("form", 10, true),
            ResidentTab("audio", 20, true),
            ResidentTab("idle", 40, false),
        )
        assertEquals(listOf("idle"), tabsToSuspend(tabs, 2))
        assertTrue(tabsToSuspend(tabs.dropLast(1), 2).isEmpty())
    }

    @Test
    fun keepsAllSessionsWithinBudgetAndAtLeastOneForInvalidBudgets() {
        val tabs = listOf(ResidentTab("old", 10, false), ResidentTab("recent", 20, false))
        assertTrue(tabsToSuspend(tabs, 5).isEmpty())
        assertEquals(listOf("old"), tabsToSuspend(tabs, 0))
        assertTrue(tabsToSuspend(emptyList(), 2).isEmpty())
    }

    @Test
    fun activeLoadingAndUnsavedFormsAreProtectedInEitherBrowsingMode() {
        for (private in listOf(false, true)) {
            val tab = createTab("https://example.org", private = private)
            val idle = tab.copy(content = tab.content.copy(loading = false))
            assertTrue(idle.isProtected(idle.id))
            assertFalse(idle.isProtected("another"))
            assertTrue(idle.copy(content = idle.content.copy(loading = true)).isProtected("another"))
            assertTrue(idle.copy(content = idle.content.copy(hasFormData = true)).isProtected("another"))
            assertTrue(idle.copy(content = idle.content.copy(pictureInPictureEnabled = true)).isProtected("another"))
        }
    }

    @Test
    fun bothPlayingAndPausedMediaAreProtected() {
        val controller = Proxy.newProxyInstance(
            MediaSession.Controller::class.java.classLoader,
            arrayOf(MediaSession.Controller::class.java),
        ) { _, method, _ -> error("Unexpected media operation: ${method.name}") } as MediaSession.Controller
        val tab = createTab("https://example.org").let { it.copy(content = it.content.copy(loading = false)) }
        for (playback in listOf(MediaSession.PlaybackState.PLAYING, MediaSession.PlaybackState.PAUSED)) {
            assertTrue(tab.copy(mediaSessionState = MediaSessionState(controller, playbackState = playback)).isProtected(null))
        }
        assertFalse(tab.copy(mediaSessionState = MediaSessionState(controller)).isProtected(null))
    }

    @Test
    fun cameraAndMicrophoneSessionsAreProtected() {
        val tab = createTab("https://example.org").let { it.copy(content = it.content.copy(loading = false)) }
        for (type in RecordingDevice.Type.entries) {
            val device = RecordingDevice(type, RecordingDevice.Status.RECORDING)
            assertTrue(tab.copy(content = tab.content.copy(recordingDevices = listOf(device))).isProtected(null))
        }
    }
}
