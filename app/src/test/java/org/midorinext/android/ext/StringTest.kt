package org.midorinext.android.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StringTest {
    private val astianGo = "https://astiango.com"

    @Test
    fun legacyHomeDetectionOnlyMatchesTheRetiredNewTabShape() {
        assertTrue("$astianGo/?omnibar=1&qbc=1".isLegacyMidoriHomeUrl())
        assertTrue("$astianGo?qbc=1&omnibar=1".isLegacyMidoriHomeUrl())

        assertFalse("$astianGo/bangs?omnibar=1&qbc=1".isLegacyMidoriHomeUrl())
        assertFalse("$astianGo/?omnibar=1&qbc=1&q=midori".isLegacyMidoriHomeUrl())
        assertFalse("https://example.com/?omnibar=1&qbc=1".isLegacyMidoriHomeUrl())
    }

    @Test
    fun extensionOriginIsNeverExposedAsAHost() {
        assertEquals("", "moz-extension://runtime-id/index.html".toCleanHost())
        assertEquals("example.com", "https://www.example.com/path".toCleanHost())
    }
}
