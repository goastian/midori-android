package org.midorinext.android.sync

import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncAccountRecoveryTest {
    private val scope = "[\"https://sync.astian.org/\",\"https://accounts.example.invalid/\",\"synthetic-account\"]"

    private data class Account(val remote: JSONObject, val local: JSONObject, val capsule: JSONObject)

    private fun account(): Account {
        val empty = obj("crypto_state_version" to 1, "epoch" to UUID.randomUUID().toString(), "revision" to "0",
            "keys" to JSONArray(), "migration_required" to false, "account_recovery" to false)
        val local = SyncKeys(scope).use { it.prepareAccount(empty) }
        val activation = local.getJSONObject("activation")
        val remote = copy(empty).put("revision", "1").put("mode", "native").put("custody", "client").put("server_recovery", false)
            .put("registry_auth_version", 2).put("registry_auth", activation.getString("registry_auth"))
            .put("identity_key", activation.getJSONObject("identity_key")).put("active_key_id", activation.getString("key_id"))
            .put("keys", JSONArray().put(obj("key_id" to activation.getString("key_id"), "encrypted_bundle" to activation.getString("encrypted_bundle"))))
            .put("account_recovery", true)
        return Account(remote, local, copy(activation.getJSONObject("account_recovery")))
    }

    @Test fun astian_account_restores_data_and_identity_without_a_recovery_code() {
        val account = account()
        assertTrue(account.local.getBoolean("confirmed"))
        assertFalse(account.capsule.toString().contains("MS2-"))
        account.capsule.put("scope", account.capsule.getString("scope").replace("/", "\\/"))
        SyncKeys(scope).use { original ->
            original.restore(account.remote, account.local)
            val id = "bookmark1234"
            val value = obj("title" to "A bookmark", "url" to "https://example.invalid/")
            val generation = UUID.randomUUID().toString()
            val payload = original.seal("bookmarks", id, generation, "0", value)
            val identity = original.derive("bookmarks", JSONArray().put("stable identity"))
            SyncKeys(scope).use { fresh ->
                val restored = fresh.restoreAccount(account.remote, account.capsule)
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

    @Test fun account_capsules_reject_cross_account_epoch_secret_and_registry_changes() {
        val account = account()
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("scope", "another account") },
            { it.put("epoch", UUID.randomUUID().toString()) },
            { it.put("secret", "0".repeat(64)) },
            { it.put("keys", JSONArray()) },
        )
        for (change in changes) {
            val capsule = copy(account.capsule).also(change)
            try { SyncKeys(scope).use { it.restoreAccount(account.remote, capsule) }; fail("Invalid account capsule accepted") }
            catch (_: Exception) { }
        }
        val badMac = copy(account.remote).put("registry_auth", "0".repeat(64))
        try { SyncKeys(scope).use { it.restoreAccount(badMac, account.capsule) }; fail("Invalid registry MAC accepted") }
        catch (_: Exception) { }
    }

    @Test fun account_recovery_preserves_the_existing_registry_checkpoint() {
        val account = account()
        val previous = SyncKeys(scope).use { it.restore(account.remote, account.local) }
        val replayed = copy(account.remote).put("revision", "0")
        try { SyncKeys(scope).use { it.restoreAccount(replayed, account.capsule, previous) }; fail("Registry replay accepted") }
        catch (error: SyncException) { assertEquals("registry_replay", error.code) }
        val changedEpoch = copy(account.remote).put("epoch", UUID.randomUUID().toString())
        try { SyncKeys(scope).use { it.restoreAccount(changedEpoch, account.capsule, previous) }; fail("Changed epoch accepted") }
        catch (error: SyncException) { assertEquals("registry_replay", error.code) }
    }
}
