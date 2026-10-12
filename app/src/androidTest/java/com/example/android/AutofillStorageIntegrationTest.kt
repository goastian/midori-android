package org.midorinext.android.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.lib.dataprotect.SecureAbove22Preferences
import mozilla.components.service.sync.logins.SyncableLoginsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.storage.autofill.LoginAutofillRepository

@RunWith(AndroidJUnit4::class)
class AutofillStorageIntegrationTest {
    @Test
    fun rootAndNestedSubdomainsKeepIndependentPasswordsForSameUsername() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val securePreferences = SecureAbove22Preferences(context, "midori_secure_storage_keys")
        val storage = SyncableLoginsStorage(context, lazy { securePreferences })
        val repository = LoginAutofillRepository(lazy { storage })
        val root = LoginEntry(TEST_ORIGIN, TEST_ORIGIN, username = "subdomains-integration-test", password = "root-password")
        val nestedOrigin = "https://accounts.mail.$TEST_DOMAIN"
        val nested = root.copy(origin = nestedOrigin, formActionOrigin = nestedOrigin, password = "nested-password")
        val savedRoot = storage.addOrUpdate(root)
        val savedNested = storage.addOrUpdate(nested)
        try {
            val rootDecision = repository.validate(root.copy(password = "root-updated"))
            val nestedDecision = repository.validate(nested.copy(password = "nested-updated"))
            assertEquals(org.midorinext.android.storage.autofill.LoginSaveDecision.Update(savedRoot.guid), rootDecision)
            assertEquals(org.midorinext.android.storage.autofill.LoginSaveDecision.Update(savedNested.guid), nestedDecision)

            assertTrue(repository.saveConfirmed(nested.copy(password = "nested-updated"), savedNested.guid))

            assertEquals(root.password, storage.get(savedRoot.guid)!!.password)
            assertEquals("nested-updated", storage.get(savedNested.guid)!!.password)
            assertTrue(repository.fetch(TEST_DOMAIN).map { it.guid }.containsAll(listOf(savedRoot.guid, savedNested.guid)))
        } finally {
            storage.delete(savedRoot.guid)
            storage.delete(savedNested.guid)
        }
    }

    @Test
    fun deletingEncryptedLoginPreventsLateSaveFromRestoringIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val securePreferences = SecureAbove22Preferences(context, "midori_secure_storage_keys")
        val storage = SyncableLoginsStorage(context, lazy { securePreferences })
        val entry = LoginEntry(
            origin = TEST_ORIGIN,
            formActionOrigin = TEST_ORIGIN,
            username = "deleted-autofill-test@example.com",
            password = "MidoriIntegration742",
        )
        val saved = storage.addOrUpdate(entry)
        val repository = LoginAutofillRepository(lazy { storage })
        try {
            val update = entry.copy(password = "MidoriIntegration843")
            repository.authorizeSave(update, saved.guid)
            val pending = repository.takeSaveAuthorization(update)!!

            repository.delete(saved.guid)
            repository.save(pending)

            assertNull(storage.get(saved.guid))
            assertNull(repository.resolveSelection(saved))
            assertTrue(storage.getByBaseDomain(TEST_DOMAIN).none { it.username == entry.username })
        } finally {
            storage.getByBaseDomain(TEST_DOMAIN).filter { it.username == entry.username }
                .forEach { storage.delete(it.guid) }
        }
    }

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
