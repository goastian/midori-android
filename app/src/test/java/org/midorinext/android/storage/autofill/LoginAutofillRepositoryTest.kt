package org.midorinext.android.storage.autofill

import kotlinx.coroutines.runBlocking
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LoginAutofillRepositoryTest {
    @Test
    fun deletingLoginRevokesSaveCallbackAlreadyInFlight() = runBlocking {
        val fixture = Fixture()
        val entry = fixture.original.toEntry()
        fixture.repository.authorizeSave(entry, fixture.original.guid)
        val pending = fixture.repository.takeSaveAuthorization(entry)!!

        fixture.repository.delete(fixture.original.guid)
        fixture.repository.save(pending)

        assertTrue(fixture.repository.fetch("example.org").isEmpty())
        assertTrue(fixture.rows.isEmpty())
    }

    @Test
    fun deletingLoginDiscardsConfirmationAwaitingGeckoCallback() = runBlocking {
        val fixture = Fixture()
        val entry = fixture.original.toEntry()
        fixture.repository.authorizeSave(entry)

        fixture.repository.delete(fixture.original.guid)

        assertNull(fixture.repository.takeSaveAuthorization(entry))
        assertNull(fixture.repository.resolveSelection(fixture.original))
    }

    @Test
    fun deletingLoginAlsoRevokesUpdatesThatChangeUsername() = runBlocking {
        val fixture = Fixture()
        val entry = fixture.original.toEntry().copy(username = "new-user")
        fixture.repository.authorizeSave(entry, fixture.original.guid)
        val pending = fixture.repository.takeSaveAuthorization(entry)!!

        fixture.repository.delete(fixture.original.guid)
        fixture.repository.save(pending)

        assertTrue(fixture.rows.isEmpty())
    }

    @Test
    fun explicitNewConfirmationCanSaveDeletedAccountAgain() = runBlocking {
        val fixture = Fixture()
        val entry = fixture.original.toEntry()
        fixture.repository.delete(fixture.original.guid)
        assertEquals(LoginSaveDecision.Create, fixture.repository.validate(entry))

        fixture.repository.authorizeSave(entry)
        fixture.repository.save(fixture.repository.takeSaveAuthorization(entry)!!)

        assertEquals(entry, fixture.repository.list().single().toEntry())
    }

    @Test
    fun stalePickerCannotFillPasswordChangedInStorage() = runBlocking {
        val fixture = Fixture()
        assertEquals(fixture.original, fixture.repository.resolveSelection(fixture.original))
        fixture.rows[fixture.original.guid] = fixture.original.copy(password = "changed")

        assertNull(fixture.repository.resolveSelection(fixture.original))
        assertNotNull(fixture.repository.resolveSelection(fixture.rows.getValue(fixture.original.guid)))
    }

    @Test
    fun pickerCanResolveGeckoCredentialsWithoutHtmlFieldMetadata() = runBlocking {
        val fixture = Fixture()
        fixture.rows[fixture.original.guid] = fixture.original.copy(usernameField = "email", passwordField = "password")

        assertNotNull(fixture.repository.resolveSelection(fixture.original))
    }

    @Test
    fun validationDistinguishesNewChangedAndUnchangedCredentials() = runBlocking {
        val fixture = Fixture()
        val entry = fixture.original.toEntry()

        assertEquals(LoginSaveDecision.Unchanged, fixture.repository.validate(entry))
        assertEquals(LoginSaveDecision.Update(fixture.original.guid), fixture.repository.validate(entry.copy(password = "changed")))
        assertEquals(LoginSaveDecision.Create, fixture.repository.validate(entry.copy(username = "other-user")))
    }

    @Test
    fun authorizationMustMatchCredentialsAndCanOnlyBeUsedOnce() {
        val fixture = Fixture()
        val entry = fixture.original.toEntry()
        fixture.repository.authorizeSave(entry)

        assertNull(fixture.repository.takeSaveAuthorization(entry.copy(password = "other")))
        assertNotNull(fixture.repository.takeSaveAuthorization(entry))
        assertNull(fixture.repository.takeSaveAuthorization(entry))
    }

    private class Fixture {
        val original = Login(
            guid = "stored-guid",
            origin = "https://example.org",
            formActionOrigin = "https://example.org",
            username = "user",
            password = "password",
        )
        val rows = mutableMapOf(original.guid to original)
        private val storage = Proxy.newProxyInstance(
            LoginsStorage::class.java.classLoader,
            arrayOf(LoginsStorage::class.java),
        ) { _, method, args ->
            when (method.name) {
                "get" -> rows[args!![0]]
                "delete" -> rows.remove(args!![0]) != null
                "list", "getByBaseDomain" -> rows.values.toList()
                "findLoginToUpdate" -> (args!![0] as LoginEntry).let { entry ->
                    rows.values.firstOrNull { it.origin == entry.origin && it.username == entry.username }
                }
                "addOrUpdate" -> (args!![0] as LoginEntry).let { entry ->
                    val guid = rows.values.firstOrNull { it.origin == entry.origin && it.username == entry.username }?.guid ?: "new-guid"
                    original.copy(guid = guid, username = entry.username, password = entry.password).also { rows[guid] = it }
                }
                else -> error("Unexpected operation: ${method.name}")
            }
        } as LoginsStorage
        val repository = LoginAutofillRepository(lazy { storage })
    }
}
