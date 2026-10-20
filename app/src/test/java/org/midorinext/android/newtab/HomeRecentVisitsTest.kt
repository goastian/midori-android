package org.midorinext.android.newtab

import org.junit.Assert.*
import org.junit.Test

class HomeRecentVisitsTest {
    @Test
    fun pagesSchemesAndWwwShareASite() {
        val pages = listOf(
            "https://www.Example.org/article?ref=home#top",
            "http://example.org/other",
            "https://example.org:443/",
            "http://www.example.org:80/",
        )
        assertEquals(listOf("example.org"), pages.map(::recentHomeSiteKey).distinct())
    }

    @Test
    fun separateServicesAndNonDefaultPortsRemainDistinct() {
        val pages = listOf(
            "https://mail.google.com/mail/u/0/",
            "https://drive.google.com/drive/",
            "https://example.org/",
            "https://example.org:8443/",
        )
        assertEquals(4, pages.map(::recentHomeSiteKey).distinct().size)
    }

    @Test
    fun rejectsInternalMalformedAndCredentialedAddresses() {
        listOf(NATIVE_HOME_URL, "about:blank", "https:///", "https://user:pass@example.org/").forEach {
            assertNull(it, recentHomeSiteKey(it))
        }
    }
}
