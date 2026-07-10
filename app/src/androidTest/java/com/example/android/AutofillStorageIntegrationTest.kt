package org.midorinext.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillStorageIntegrationTest {
    @Test
    fun encryptedLoginStoragePersistsAndReadsCredentials() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val securePreferences = SecureAbove22Preferences(context, "midori_secure_storage_keys")
        val storage = SyncableLoginsStorage(context, lazy { securePreferences })
        val entry = LoginEntry(
            origin = TEST_ORIGIN,
            formActionOrigin = TEST_ORIGIN,
            usernameField = "email",
            passwordField = "password",
            username = "autofill-test@example.com",
            password = "MidoriIntegration742",
        )

        val saved = storage.addOrUpdate(entry)
        try {
            val matches = storage.getByBaseDomain(TEST_DOMAIN)
            assertTrue(matches.any { it.guid == saved.guid })
            assertEquals(entry.username, matches.single { it.guid == saved.guid }.username)
            assertEquals(entry.password, matches.single { it.guid == saved.guid }.password)
        } finally {
            storage.delete(saved.guid)
        }
    }

    private companion object {
        const val TEST_DOMAIN = "autofill.integration.test"
        const val TEST_ORIGIN = "https://$TEST_DOMAIN"
    }
}
