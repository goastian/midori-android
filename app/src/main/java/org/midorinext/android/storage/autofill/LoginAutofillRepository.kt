package org.midorinext.android.storage.autofill

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginEntry
import mozilla.components.concept.storage.LoginHint
import mozilla.components.concept.storage.LoginsStorage

class LoginAutofillRepository(private val storage: Lazy<LoginsStorage>) {
    private val mutex = Mutex()
    private val authorizationLock = Any()
    private val revisions = mutableMapOf<LoginIdentity, Long>()
    private val pendingSaves = mutableMapOf<LoginIdentity, SaveAuthorization>()

    suspend fun list(): List<Login> = withStorage { list() }

    suspend fun fetch(domain: String): List<Login> = withStorage { getByBaseDomain(domain) }

    suspend fun markUsed(guid: String) = withStorage {
        if (get(guid) != null) touch(guid)
    }

    suspend fun resolveSelection(login: Login): Login? {
        if (login.hint == LoginHint.GENERATED && login.guid.isEmpty()) return login
        if (login.guid.isEmpty()) return null
        return withStorage {
            get(login.guid)?.takeIf {
                sameLoginOrigin(it.origin, login.origin) && it.httpRealm == login.httpRealm &&
                    it.username == login.username && it.password == login.password
            }?.let { login }
        }
    }

    suspend fun validate(entry: LoginEntry): LoginSaveDecision = withStorage {
        val existing = findLoginToUpdate(entry)
        when {
            existing == null -> LoginSaveDecision.Create
            existing.username == entry.username && existing.password == entry.password -> LoginSaveDecision.Unchanged
            else -> LoginSaveDecision.Update(existing.guid)
        }
    }

    fun authorizeSave(entry: LoginEntry, existingGuid: String? = null) {
        synchronized(authorizationLock) {
            val identity = entry.identity()
            pendingSaves[identity] = SaveAuthorization(entry, existingGuid, revisions[identity] ?: 0L)
        }
    }

    suspend fun saveConfirmed(entry: LoginEntry, existingGuid: String?): Boolean {
        val authorization = synchronized(authorizationLock) {
            SaveAuthorization(entry, existingGuid, revisions[entry.identity()] ?: 0L)
        }
        return save(authorization)
    }

    internal fun takeSaveAuthorization(entry: LoginEntry): SaveAuthorization? = synchronized(authorizationLock) {
        val identity = entry.identity()
        pendingSaves[identity]?.takeIf { it.entry == entry }?.also { pendingSaves.remove(identity) }
    }

    internal suspend fun save(authorization: SaveAuthorization): Boolean = withStorage {
        val current = synchronized(authorizationLock) {
            (revisions[authorization.entry.identity()] ?: 0L) == authorization.revision
        }
        if (!current) return@withStorage false
        val guid = authorization.existingGuid
        if (guid == null) {
            add(authorization.entry)
        } else {
            if (get(guid) == null) return@withStorage false
            update(guid, authorization.entry)
        }
        true
    }

    suspend fun delete(guid: String): Unit = withStorage {
        val login = get(guid)
        delete(guid)
        synchronized(authorizationLock) {
            login?.toEntry()?.identity()?.let { identity ->
                revisions[identity] = (revisions[identity] ?: 0L) + 1L
                pendingSaves.remove(identity)
            }
            pendingSaves.entries.removeAll { it.value.existingGuid == guid }
        }
    }

    private suspend fun <T> withStorage(operation: suspend LoginsStorage.() -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { storage.value.operation() } }

    internal data class SaveAuthorization(val entry: LoginEntry, val existingGuid: String?, val revision: Long)

    private data class LoginIdentity(
        val origin: String,
        val formActionOrigin: String?,
        val httpRealm: String?,
        val username: String,
    )

    private fun LoginEntry.identity() = LoginIdentity(origin, formActionOrigin, httpRealm, username)
}

sealed interface LoginSaveDecision {
    data object Create : LoginSaveDecision
    data class Update(val guid: String) : LoginSaveDecision
    data object Unchanged : LoginSaveDecision
}
