package org.midorinext.android.pwa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAppRepositoryTest {
    @Test
    fun defaultScopeKeepsTheAppPath() {
        assertEquals(
            "https://example.org/tools/",
            WebAppRepository.defaultScope("https://example.org/tools/start?mode=app"),
        )
    }

    @Test
    fun scopeIncludesOnlyTheSameOriginAndPath() {
        val scope = "https://example.org/tools/"
        assertTrue(WebAppRepository.isWithinScope("https://example.org/tools/page", scope))
        assertTrue(WebAppRepository.isWithinScope("https://example.org:443/tools/page", scope))
        assertFalse(WebAppRepository.isWithinScope("https://example.org/toolshed/", scope))
        assertFalse(WebAppRepository.isWithinScope("https://other.example.org/tools/page", scope))
        assertFalse(WebAppRepository.isWithinScope("http://example.org/tools/page", scope))
        assertFalse(WebAppRepository.isWithinScope("https://example.org:8443/tools/page", scope))
        assertFalse(WebAppRepository.isWithinScope("not a URL", scope))
    }
}
