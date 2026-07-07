package org.midorinext.android.apptracking.packet

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTrackingRuleEngineTest {

    private val engine = AppTrackingRuleEngine()

    @Test
    fun blocksKnownTrackerDomain() {
        assertTrue(engine.shouldBlock("google-analytics.com"))
        assertTrue(engine.shouldBlock("app-measurement.doubleclick.net"))
        assertTrue(engine.shouldBlock("connect.facebook.net"))
    }

    @Test
    fun allowsRegularContentDomain() {
        assertFalse(engine.shouldBlock("m.youtube.com"))
        assertFalse(engine.shouldBlock("example.org"))
        assertFalse(engine.shouldBlock("facebook.com"))
        assertFalse(engine.shouldBlock("google.com"))
    }

    @Test
    fun classifiesKnownTrackerDomain() {
        val match = engine.classify("app-measurement.doubleclick.net")

        assertNotNull(match)
        assertEquals("Google", match?.company)
        assertTrue(match?.categories?.contains("Ad Attribution") == true)
    }
}


