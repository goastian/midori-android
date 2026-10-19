package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.security.MessageDigest

internal object RustAdblock {
    init {
        val path = System.getProperty("midori.adblock.library")
        if (path == null) System.loadLibrary("midori_adblock") else System.load(path)
    }

    external fun create(lists: String, resources: String): Long
    external fun release(handle: Long)
    external fun query(handle: Long, input: String): String
    external fun cosmetics(handle: Long, input: String): String
    external fun serialize(handle: Long): ByteArray
    external fun restore(data: ByteArray, resources: String): Long
    external fun countRules(text: String): Long
}

internal class RustFilterEngine private constructor(private val handle: Long) : Closeable {
    fun query(url: String, source: String, type: String, method: String = "GET", document: String = ""): JSONObject =
        JSONObject(RustAdblock.query(handle, JSONObject()
            .put("url", url).put("source", source).put("type", type)
            .put("method", method).put("document", document).toString()))

    fun cosmetics(url: String, classes: List<String> = emptyList(), ids: List<String> = emptyList()): JSONObject =
        JSONObject(RustAdblock.cosmetics(handle, JSONObject().put("url", url)
            .put("classes", JSONArray(classes)).put("ids", JSONArray(ids)).toString()))

    fun serialize(): ByteArray = RustAdblock.serialize(handle)

    override fun close() = RustAdblock.release(handle)

    companion object {
        const val REVISION = "6028a6d5735f1e81346eeef73c194c7240035248"
        fun fingerprint(lists: List<String>, resources: String): String = MessageDigest.getInstance("SHA-256")
            .digest(("1:" + REVISION + resources + JSONArray(lists)).toByteArray())
            .joinToString("") { "%02x".format(it) }
        fun compile(lists: List<String>, resources: String = "[]") = RustFilterEngine(RustAdblock.create(JSONArray(lists).toString(), resources))
        fun restore(data: ByteArray, resources: String = "[]") = RustFilterEngine(RustAdblock.restore(data, resources))
    }
}
