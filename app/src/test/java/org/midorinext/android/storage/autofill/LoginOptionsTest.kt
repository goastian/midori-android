package org.midorinext.android.storage.autofill

import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginOptionsTest {
    @Test
    fun exactSiteComesBeforeMoreRecentlyUsedSubdomains() {
        val root = login("root", "https://example.org", "root-password").copy(timeLastUsed = 500)
        val nested = login("nested", "https://accounts.mail.example.org", "nested-password")
        val sibling = login("sibling", "https://app.example.org", "sibling-password").copy(timeLastUsed = 900)

        assertEquals(listOf(nested, sibling, root), loginOptions(listOf(root, sibling, nested), "https://accounts.mail.example.org/login"))
    }

    @Test
    fun identicalCredentialsAcrossSubdomainsShowBestMatchOnce() {
        val root = login("root", "https://example.org", "shared-password")
        val child = root.copy(guid = "child", origin = "https://app.example.org")

        assertEquals(listOf(child), loginOptions(listOf(root, child), "https://app.example.org/login"))
    }

    @Test
    fun engineMatchingFormOriginTakesPriorityOverTopLevelPage() {
        val page = login("page", "https://example.org", "page-password")
        val frame = login("frame", "https://accounts.example.org", "frame-password").copy(hint = LoginHint.MATCHING_ORIGIN)

        assertEquals(listOf(frame, page), loginOptions(listOf(page, frame), page.origin))
    }

    @Test
    fun sameUsernameWithDifferentPasswordsRemainsSelectable() {
        val root = login("root", "https://example.org", "root-password")
        val child = root.copy(guid = "child", origin = "https://app.example.org", password = "child-password")

        assertEquals(listOf(child, root), loginOptions(listOf(root, child), "https://app.example.org/login"))
    }

    @Test
    fun generatedPasswordIsPreservedAndUnsupportedEmailMaskIsExcluded() {
        val stored = login("stored", "https://example.org", "password")
        val generated = stored.copy(guid = "", username = "", hint = LoginHint.GENERATED)
        val mask = stored.copy(guid = "mask", hint = LoginHint.EMAIL_MASK)

        assertEquals(listOf(generated, stored), loginOptions(listOf(stored, mask, generated), stored.origin))
    }

    @Test
    fun originMatchingHandlesNormalizationWithoutConfusingSitesOrPorts() {
        assertTrue(sameLoginOrigin("https://EXAMPLE.org:443", "https://example.org/login"))
        assertFalse(sameLoginOrigin("https://example.org", "https://other.example.org"))
        assertFalse(sameLoginOrigin("https://example.org", "https://example.org.attacker.test"))
        assertFalse(sameLoginOrigin("https://example.org", "http://example.org"))
        assertFalse(sameLoginOrigin("https://example.org:8443", "https://example.org"))
    }

    private fun login(guid: String, origin: String, password: String) = Login(guid, "same-user", password, origin, origin)
}
