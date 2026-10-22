package org.midorinext.android.adblock

import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.security.MessageDigest

internal object RustAdblock {
    init {
        val path = System.getProperty("midori.adblock.library")
        if (path == null) System.loadLibrary("midori_adblock") else System.load(path)
    }

    external fun create(lists: String, resources: String): Long
    external fun createFromFiles(paths: String, resources: String): Long
    external fun release(handle: Long)
    external fun query(handle: Long, input: String): String
    external fun cosmetics(handle: Long, input: String): String
    external fun serialize(handle: Long): ByteArray
    external fun serializeToFile(handle: Long, path: String)
    external fun restore(data: ByteArray, resources: String): Long
    external fun restoreFromFile(path: String, resources: String): Long
    external fun countRules(text: String): Long
    external fun countRulesInFile(path: String): Long
}

/**
 * Filter text a list contributes to the native engine. The text stays on disk: the native side reads the file
 * itself, so neither the Java heap nor a JNI string ever holds a whole list.
 */
internal data class EngineList(val file: File, val sha256: String, val length: Long)

internal class RustFilterEngine private constructor(private val handle: Long) : Closeable {
    fun query(url: String, source: String, type: String, method: String = "GET", document: String = ""): JSONObject =
        JSONObject(RustAdblock.query(handle, JSONObject()
            .put("url", url).put("source", source).put("type", type)
            .put("method", method).put("document", document).toString()))

    fun cosmetics(url: String, classes: List<String> = emptyList(), ids: List<String> = emptyList()): JSONObject =
        JSONObject(RustAdblock.cosmetics(handle, JSONObject().put("url", url)
            .put("classes", JSONArray(classes)).put("ids", JSONArray(ids)).toString()))

    fun serialize(): ByteArray = RustAdblock.serialize(handle)

    fun serializeTo(file: File) = RustAdblock.serializeToFile(handle, file.path)

    override fun close() = RustAdblock.release(handle)

    companion object {
        const val REVISION = "6028a6d5735f1e81346eeef73c194c7240035248"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        fun fingerprint(lists: List<EngineList>, resourcesSha256: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("2:$REVISION:$resourcesSha256".toByteArray())
            lists.forEach { digest.update(":${it.sha256}".toByteArray()) }
            return digest.digest().toHex()
        }

        fun compile(lists: List<String>, resources: String = "[]") = RustFilterEngine(RustAdblock.create(JSONArray(lists).toString(), resources))
        fun compileFiles(files: List<File>, resources: String = "[]") =
            RustFilterEngine(RustAdblock.createFromFiles(JSONArray(files.map { it.path }).toString(), resources))
        fun restore(data: ByteArray, resources: String = "[]") = RustFilterEngine(RustAdblock.restore(data, resources))
        fun restoreFile(file: File, resources: String = "[]") = RustFilterEngine(RustAdblock.restoreFromFile(file.path, resources))
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
