package org.midorinext.android.sync

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncAccountRecoveryTest {
    private val scope = "[\"https://sync.astian.org/\",\"https://accounts.example.invalid/\",\"synthetic-account\"]"

    private data class Account(val remote: JSONObject, val local: JSONObject, val code: String)

    private fun account(): Account {
        val empty = obj("crypto_state_version" to 1, "epoch" to UUID.randomUUID().toString(), "revision" to "0",
            "keys" to JSONArray(), "migration_required" to false, "account_recovery" to true)
        val (code, local) = SyncKeys(scope).use { it.prepare(empty) }
        val activation = local.getJSONObject("activation")
        val remote = copy(empty).put("revision", "1").put("mode", "native").put("custody", "client").put("server_recovery", false)
            .put("registry_auth_version", 2).put("registry_auth", activation.getString("registry_auth"))
            .put("identity_key", activation.getJSONObject("identity_key")).put("active_key_id", activation.getString("key_id"))
            .put("keys", JSONArray().put(obj("key_id" to activation.getString("key_id"), "encrypted_bundle" to activation.getString("encrypted_bundle"))))
        return Account(remote, local, code)
    }

    @Test fun activation_never_contains_the_local_secret_or_recovery_code() {
        val account = account()
        assertFalse(account.local.optBoolean("confirmed"))
        val wire = SyncKeys.activation(account.local).toString()
        assertFalse(wire.contains("account_recovery"))
        assertFalse(wire.contains("secret"))
        assertFalse(wire.contains(account.local.getString("secret")))
        assertFalse(wire.contains(account.code))
        assertFalse(wire.contains(account.code.removePrefix("MS2-").replace("-", "").lowercase()))
    }

    @Test fun persisted_activations_from_old_clients_cannot_republish_escrow() {
        val account = account()
        account.local.getJSONObject("activation")
            .put("account_recovery", obj("secret" to account.local.getString("secret"), "keys" to account.local.getJSONArray("keys")))
            .put("recovery_secret", "a".repeat(64)).put("secret", account.local.getString("secret"))
        val safe = SyncKeys.activation(account.local)
        assertFalse(safe.has("account_recovery"))
        assertFalse(safe.has("recovery_secret"))
        assertFalse(safe.has("secret"))
        assertEquals(canonical(safe), canonical(account.local.getJSONObject("activation")))
        SyncKeys(scope).use { it.restore(account.remote, account.local) }
    }

    @Test fun login_and_server_recovery_flags_are_insufficient_to_decrypt() {
        val account = account()
        for (advertised in listOf(true, false)) {
            account.remote.put("account_recovery", advertised)
            try { SyncKeys(scope).use { it.restore(account.remote, null) }; fail("Server-only recovery accepted") }
            catch (error: SyncException) { assertEquals("recovery_required", error.code) }
        }
    }

    @Test fun user_recovery_code_restores_data_and_identity_and_rotates_local_wrapping() {
        val account = account()
        SyncKeys(scope).use { original ->
            original.restore(account.remote, account.local)
            val id = "bookmark1234"
            val value = obj("title" to "A bookmark", "url" to "https://example.invalid/")
            val generation = UUID.randomUUID().toString()
            val payload = original.seal("bookmarks", id, generation, "0", value)
            val identity = original.derive("bookmarks", JSONArray().put("stable identity"))
            SyncKeys(scope).use { fresh ->
                val restored = fresh.restore(account.remote, null, account.code)
                assertEquals(identity, fresh.derive("bookmarks", JSONArray().put("stable identity")))
                assertEquals(canonical(value), canonical(fresh.open("bookmarks", generation, obj("id" to id, "revision" to "1",
                    "deleted" to false, "ttl" to null, "payload" to payload))))
                assertNotEquals(account.local.getString("secret"), restored.getString("secret"))
                SyncKeys(scope).use { reopened ->
                    reopened.restore(account.remote, restored)
                    assertEquals(identity, reopened.derive("bookmarks", JSONArray().put("stable identity")))
                }
            }
        }
    }

    @Test fun wrong_code_scope_mac_and_replayed_registry_cannot_restore_keys() {
        val account = account()
        val attempts = listOf(
            Triple(scope, account.remote, "MS2-" + "00000000-".repeat(7) + "00000000"),
            Triple(scope.replace("synthetic-account", "other-account"), account.remote, account.code),
            Triple(scope, copy(account.remote).put("registry_auth", "0".repeat(64)), account.code),
        )
        for ((keyScope, remote, code) in attempts) {
            try { SyncKeys(keyScope).use { it.restore(remote, null, code) }; fail("Untrusted recovery accepted") }
            catch (_: Exception) { }
        }
        val previous = SyncKeys(scope).use { it.restore(account.remote, account.local) }
        try { SyncKeys(scope).use { it.restore(copy(account.remote).put("revision", "0"), previous) }; fail("Registry replay accepted") }
        catch (error: SyncException) { assertEquals("registry_replay", error.code) }
    }
}
