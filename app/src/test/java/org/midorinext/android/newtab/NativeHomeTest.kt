package org.midorinext.android.newtab

import org.junit.Assert.*
import org.junit.Test
import org.midorinext.android.newtab.migration.LEGACY_NEW_TAB_URL
import org.midorinext.android.newtab.migration.LEGACY_NATIVE_HOME_URL

class NativeHomeTest {
    @Test
    fun nativeHomeAndTheRetiredLoadingMarkerAreRecognized() {
        assertTrue(isNativeHomeUrl(NATIVE_HOME_URL))
        assertTrue(isNativeHomeUrl(LEGACY_NEW_TAB_URL))
        assertTrue(isNativeHomeUrl(LEGACY_NATIVE_HOME_URL))
        assertFalse(isNativeHomeUrl("about:blank"))
        assertFalse(isNativeHomeUrl("data:text/html,<title>Web page</title>"))
        assertFalse(isNativeHomeUrl(null))
    }

    @Test
    fun shortcutsRequirePublicWebAddresses() {
        assertEquals("https://example.org", publicHomeUrl(" example.org "))
        assertEquals("http://example.org/page", publicHomeUrl("http://example.org/page"))
        listOf("", "javascript:alert(1)", "data:text/html,test", "file:///private",
            "https://user:password@example.org", "https://", "invalid address").forEach {
            assertNull(it, publicHomeUrl(it))
        }
    }
}
