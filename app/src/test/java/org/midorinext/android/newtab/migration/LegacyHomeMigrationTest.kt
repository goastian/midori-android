package org.midorinext.android.newtab.migration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyHomeMigrationTest {
    @Test
    fun onlyTheRegisteredLegacyExtensionHomepageIsReplaced() {
        val origins = setOf("moz-extension://legacy-id/")
        listOf("moz-extension://legacy-id/", "moz-extension://legacy-id/index.html",
            "moz-extension://legacy-id/index.html#section").forEach {
            assertTrue(it, LegacyHomeMigration.isLegacyExtensionHome(it, origins))
        }
        listOf("moz-extension://other-id/index.html", "moz-extension://legacy-id/settings.html",
            "moz-extension://legacy-id/index.html?q=privacy", "https://legacy-id/index.html",
            "not a URI").forEach {
            assertFalse(it, LegacyHomeMigration.isLegacyExtensionHome(it, origins))
        }
        assertFalse(LegacyHomeMigration.isLegacyExtensionHome("moz-extension://legacy-id/", emptySet()))
    }
}
