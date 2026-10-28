package org.midorinext.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

internal class SyncKeys(private val scope: String) : AutoCloseable {
    private val data = SyncCrypto()
    private val identity = SyncCrypto()
    private val handles = mutableMapOf<String, Int>()
    private var identityHandle = 0
    lateinit var state: JSONObject
        private set
    val context: JSONObject get() = obj("epoch" to state.getString("epoch"), "revision" to state.getString("revision"))

    fun restore(remote: JSONObject, local: JSONObject?, code: String? = null): JSONObject {
        validateState(remote)
        requireSync(remote.objects("keys").isNotEmpty(), "keys_empty")
        requireSync(local == null || local.getString("epoch") == remote.getString("epoch"), "crypto_epoch_changed")
        requireSync(local == null || remote.getString("revision").toLong() >= local.getString("revision").toLong(), "registry_replay")
        val secret = if (code == null) local?.getString("secret") ?: throw SyncException("recovery_required") else {
            val hex = code.trim().replace(Regex("^MS2-", RegexOption.IGNORE_CASE), "").replace(Regex("[-\\s]"), "").lowercase()
            requireSync(hex.matches(Regex("[0-9a-f]{64}")), "invalid_recovery_code")
            base64(hex.toByteArray())
        }
        val localSecret = if (code == null) secret else base64(randomBytes(32))
        val savedKeys = JSONArray()
        for (key in remote.objects("keys")) {
            val id = key.getString("key_id")
            val source = if (code == null) local!!.objects("keys").find { it.getString("key_id") == id }
                ?: throw SyncException("recovery_required") else key
            val handle = (data.call("recover", "scope" to scope, "secret" to secret, "bundle" to source.getString("encrypted_bundle")) as Number).toInt()
            requireSync(data.call("key_id", "handle" to handle) == id, "recovery_failed")
            handles[id] = handle
            savedKeys.put(obj("key_id" to id, "encrypted_bundle" to if (code == null) source.getString("encrypted_bundle") else data.call("wrap", "handle" to handle, "secret" to localSecret)))
        }
        val sourceIdentity = if (code == null) local!!.getJSONObject("identity_key") else remote.getJSONObject("identity_key")
        identityHandle = (identity.call("recover", "scope" to scope, "secret" to secret,
            "bundle" to sourceIdentity.getString("encrypted_bundle")) as Number).toInt()
        requireSync(identity.call("key_id", "handle" to identityHandle) == remote.getJSONObject("identity_key").getString("key_id"), "recovery_failed")
        verify(remote)
        state = copy(remote)
        return obj("epoch" to remote.getString("epoch"), "revision" to remote.getString("revision"), "secret" to localSecret,
            "keys" to savedKeys, "identity_key" to obj("key_id" to sourceIdentity.getString("key_id"),
                "encrypted_bundle" to if (code == null) sourceIdentity.getString("encrypted_bundle") else identity.call("wrap", "handle" to identityHandle, "secret" to localSecret)))
    }

    fun refresh(remote: JSONObject) {
        validateState(remote)
        requireSync(remote.getString("epoch") == state.getString("epoch") && remote.getString("revision").toLong() >= state.getString("revision").toLong(), "registry_replay")
        requireSync(remote.objects("keys").map { it.getString("key_id") }.toSet() == handles.keys &&
            remote.getJSONObject("identity_key").getString("key_id") == state.getJSONObject("identity_key").getString("key_id"), "recovery_required")
        verify(remote)
        state = copy(remote)
    }

    fun accountCapsule(local: JSONObject, remote: JSONObject): JSONObject {
        requireSync(local.getString("epoch") == remote.getString("epoch") &&
            local.getJSONObject("identity_key").getString("key_id") == remote.getJSONObject("identity_key").getString("key_id"), "invalid_account_capsule")
        val saved = local.objects("keys").associateBy { it.getString("key_id") }
        val keys = JSONArray()
        for (key in remote.objects("keys")) keys.put(saved[key.getString("key_id")] ?: throw SyncException("invalid_account_capsule"))
        val bytes = Base64.getDecoder().decode(local.getString("secret"))
        try {
            requireSync(bytes.size == 32, "invalid_account_capsule")
            return obj("version" to 1, "scope" to scope, "epoch" to remote.getString("epoch"),
                "secret" to bytes.joinToString("") { "%02x".format(it) }, "keys" to keys,
                "identity_key" to local.getJSONObject("identity_key"))
        } finally { bytes.fill(0) }
    }

    fun restoreAccount(remote: JSONObject, capsule: JSONObject, previous: JSONObject? = null): JSONObject {
        validateState(remote)
        requireSync(previous == null || previous.getString("epoch") == remote.getString("epoch") &&
            remote.getString("revision").toLong() >= previous.getString("revision").toLong(), "registry_replay")
        requireSync(capsule.getInt("version") == 1 && capsule.getString("scope") == scope &&
            capsule.getString("epoch") == remote.getString("epoch") &&
            capsule.getString("secret").matches(Regex("[0-9a-f]{64}")) &&
            capsule.objects("keys").size == remote.objects("keys").size &&
            capsule.objects("keys").map { it.getString("key_id") }.toSet() == remote.objects("keys").map { it.getString("key_id") }.toSet() &&
            capsule.getJSONObject("identity_key").getString("key_id") == remote.getJSONObject("identity_key").getString("key_id"), "invalid_account_capsule")
        val bytes = capsule.getString("secret").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val local = try {
            restore(remote, obj("epoch" to remote.getString("epoch"), "revision" to remote.getString("revision"),
                "secret" to base64(bytes), "keys" to capsule.getJSONArray("keys"), "identity_key" to capsule.getJSONObject("identity_key")))
        } finally { bytes.fill(0) }
        val secret = base64(randomBytes(32))
        val keys = JSONArray()
        for ((id, handle) in handles) keys.put(obj("key_id" to id, "encrypted_bundle" to data.call("wrap", "handle" to handle, "secret" to secret)))
        return local.put("secret", secret).put("keys", keys).put("identity_key", obj("key_id" to remote.getJSONObject("identity_key").getString("key_id"),
            "encrypted_bundle" to identity.call("wrap", "handle" to identityHandle, "secret" to secret)))
    }

    fun prepareAccount(remote: JSONObject): JSONObject {
        val local = prepare(remote).second
        val activation = local.getJSONObject("activation")
        val next = copy(remote).put("keys", JSONArray().put(obj("key_id" to activation.getString("key_id"),
            "encrypted_bundle" to activation.getString("encrypted_bundle")))).put("identity_key", activation.getJSONObject("identity_key"))
        activation.put("account_recovery", accountCapsule(local, next))
        return local.put("confirmed", true)
    }

    fun prepare(remote: JSONObject): Pair<String, JSONObject> {
        validateState(remote, empty = true)
        requireSync(remote.objects("keys").isEmpty() && !remote.getBoolean("migration_required"), "native_keys_already_active")
        val hex = randomBytes(32).joinToString("") { "%02x".format(it) }
        val code = "MS2-" + hex.uppercase().chunked(8).joinToString("-")
        val passphrase = base64(hex.toByteArray())
        val secret = base64(randomBytes(32))
        val dataHandle = (data.call("generate", "scope" to scope) as Number).toInt()
        val id = data.call("key_id", "handle" to dataHandle) as String
        handles[id] = dataHandle
        identityHandle = (identity.call("generate", "scope" to scope) as Number).toInt()
        val identityId = identity.call("key_id", "handle" to identityHandle) as String
        val cloudData = obj("key_id" to id, "encrypted_bundle" to data.call("wrap", "handle" to dataHandle, "secret" to passphrase))
        val cloudIdentity = obj("key_id" to identityId, "encrypted_bundle" to identity.call("wrap", "handle" to identityHandle, "secret" to passphrase))
        val next = copy(remote).put("revision", (remote.getString("revision").toLong() + 1).toString())
            .put("active_key_id", id).put("keys", JSONArray().put(cloudData)).put("identity_key", cloudIdentity)
            .put("custody", "client").put("server_recovery", false).put("rotation", JSONObject.NULL).put("registry_auth_version", 2)
        val body = obj("crypto" to obj("epoch" to remote.getString("epoch"), "revision" to remote.getString("revision")),
            "key_id" to id, "encrypted_bundle" to cloudData.getString("encrypted_bundle"), "identity_key" to cloudIdentity,
            "custody" to "client", "registry_auth_version" to 2, "registry_auth" to identity.call("registry", "handle" to identityHandle, "state" to next))
        val local = obj("epoch" to remote.getString("epoch"), "revision" to remote.getString("revision"), "secret" to secret,
            "keys" to JSONArray().put(obj("key_id" to id, "encrypted_bundle" to data.call("wrap", "handle" to dataHandle, "secret" to secret))),
            "identity_key" to obj("key_id" to identityId, "encrypted_bundle" to identity.call("wrap", "handle" to identityHandle, "secret" to secret)),
            "activation" to body)
        return code to local
    }
    fun verify(remote: JSONObject) {
        validateState(remote)
        requireSync(identity.call("registry", "handle" to identityHandle, "state" to remote) == remote.getString("registry_auth"), "registry_authentication_failed")
    }
    fun derive(collection: String, input: JSONArray): String = identity.call("derive", "handle" to identityHandle,
        "collection" to collection, "input" to input.toString()) as String
    fun model(collection: String, request: JSONObject): JSONObject = data.call(collection, "request" to request.toString()) as JSONObject
    fun seal(collection: String, id: String, generation: String, revision: String, value: JSONObject?): String {
        val context = obj("collection" to collection, "id" to id, "generation" to generation, "base_revision" to revision, "schema_version" to 2)
        return data.call("seal", "handle" to handles.getValue(state.getString("active_key_id")), "context" to context,
            "plaintext" to obj("version" to 2, "deleted" to (value == null), "ttl" to null, "value" to value).toString()) as String
    }
    fun open(collection: String, generation: String, record: JSONObject): JSONObject? {
        val envelope = JSONObject(record.getString("payload"))
        val context = envelope.getJSONObject("context")
        requireSync(context.getInt("schema_version") == 2 && context.getString("base_revision").toLong() + 1 == record.getString("revision").toLong(), "record_revision_mismatch")
        val id = envelope.getString("key_id")
        requireSync(state.getString("custody") != "client" || id == state.getString("active_key_id"), "record_key_retired")
        val value = JSONObject(data.call("open", "handle" to (handles[id] ?: throw SyncException("unknown_key")),
            "locator" to obj("collection" to collection, "id" to record.getString("id"), "generation" to generation), "envelope" to record.getString("payload")) as String)
        requireSync(value.length() == 4 && value.getInt("version") == 2 && value.getBoolean("deleted") == record.getBoolean("deleted") &&
            same(value.opt("ttl"), record.opt("ttl")) && (!record.getBoolean("deleted") || value.isNull("value")), "record_authentication_failed")
        requireSync(value.isNull("ttl"), "record_ttl_unsupported")
        return value.objectOrNull("value")
    }
    override fun close() { data.close(); identity.close() }
    private fun validateState(value: JSONObject, empty: Boolean = false) {
        requireSync(value.getInt("crypto_state_version") == 1 && value.getString("epoch").matches(UUID_PATTERN) &&
            value.getString("revision").matches(Regex("0|[1-9][0-9]{0,9}")) && value.getString("revision").toLong() <= Int.MAX_VALUE &&
            value.objects("keys").size <= 8, "invalid_crypto_state")
        if (!empty) requireSync(value.getInt("registry_auth_version") == 2 && value.objectOrNull("identity_key") != null &&
            value.getString("custody") in listOf("client", "transition"), "client_upgrade_required")
    }
}
internal val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
