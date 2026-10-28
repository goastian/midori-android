package org.midorinext.android.sync

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal fun obj(vararg fields: Pair<String, Any?>) = JSONObject().apply {
    fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) }
}
internal fun JSONObject.objectOrNull(key: String) = opt(key) as? JSONObject
internal fun JSONObject.textOrNull(key: String) = opt(key) as? String
internal fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { array ->
    (0 until array.length()).map { array.getJSONObject(it) }
}.orEmpty()
internal fun JSONObject.entries(): List<Pair<String, Any>> = keys().asSequence().map { it to get(it) }.toList()
internal fun copy(value: JSONObject) = JSONObject(value.toString())
internal fun canonical(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
    is String -> JSONObject.quote(value)
    else -> value.toString()
}
internal fun same(a: Any?, b: Any?) = canonical(a) == canonical(b)
internal fun randomBytes(count: Int) = ByteArray(count).also { SecureRandom().nextBytes(it) }
internal fun base64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
internal fun base64url(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
internal fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
internal fun portableGuid(value: String): String = if (Regex("[A-Za-z0-9_-]{12}").matches(value)) value else base64url(digest(value.toByteArray()).copyOf(9))
internal fun requireSync(condition: Boolean, code: String = "invalid_record") { if (!condition) throw SyncException(code) }
class SyncException(val code: String, val status: Int = 0, val retryAfter: Long = 0) : Exception(code)

internal class SyncCrypto : AutoCloseable {
    private var ring = NativeSync.create()
    @Synchronized fun call(action: String, vararg fields: Pair<String, Any?>): Any {
        check(ring != 0L)
        val input = obj("action" to action, *fields).toString().toByteArray()
        return try {
            val output = NativeSync.request(ring, input)
            try { JSONTokener(output.toString(Charsets.UTF_8)).nextValue() } finally { output.fill(0) }
        } finally { input.fill(0) }
    }
    override fun close() { synchronized(this) { if (ring != 0L) NativeSync.close(ring); ring = 0 } }
}

internal object NativeSync {
    init {
        System.getProperty("midori.sync.library")?.let { System.load(it) } ?: System.loadLibrary("midori_sync_android")
    }
    @JvmStatic external fun create(): Long
    @JvmStatic external fun request(ring: Long, input: ByteArray): ByteArray
    @JvmStatic external fun close(ring: Long)
}
