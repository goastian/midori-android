package org.midorinext.android.newtab

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MidoriNewTabFeatureTest {
    @Test
    fun nativeHomeAndTheRetiredLoadingMarkerAreRecognized() {
        assertTrue(isNativeHomeUrl(NATIVE_HOME_URL))
        assertTrue(isNativeHomeUrl(LEGACY_NEW_TAB_URL))
        assertTrue(isNativeHomeUrl(LEGACY_NATIVE_HOME_URL))
        assertFalse(isNativeHomeUrl("about:blank"))
        assertFalse(isNativeHomeUrl("data:text/html,<title>Web page</title>"))
        assertFalse(isNativeHomeUrl(null))
        assertFalse(File("src/main/assets/extensions/midori_newtab").exists())
    }

    @Test
    fun onlyTheRegisteredLegacyExtensionHomepageIsReplaced() {
        val origins = setOf("moz-extension://legacy-id/")
        listOf("moz-extension://legacy-id/", "moz-extension://legacy-id/index.html",
            "moz-extension://legacy-id/index.html#section").forEach {
            assertTrue(it, MidoriNewTabFeature.isLegacyExtensionHome(it, origins))
        }
        listOf("moz-extension://other-id/index.html", "moz-extension://legacy-id/settings.html",
            "moz-extension://legacy-id/index.html?q=privacy", "https://legacy-id/index.html").forEach {
            assertFalse(it, MidoriNewTabFeature.isLegacyExtensionHome(it, origins))
        }
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
