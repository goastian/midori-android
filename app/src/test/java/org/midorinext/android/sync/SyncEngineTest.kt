package org.midorinext.android.sync

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SyncEngineTest {
    @Test fun encrypted_clients_converge_and_replay_a_lost_response_without_losing_local_edits() = runBlocking {
        val empty = obj("crypto_state_version" to 1, "epoch" to UUID.randomUUID().toString(), "revision" to "0", "keys" to JSONArray(),
            "migration_required" to false)
        val scope = "[\"http://localhost:8000/\",\"https://accounts.example.invalid/\",\"synthetic-mobile\"]"
        val (code, local) = SyncKeys(scope).use { it.prepare(empty) }
        val activation = local.getJSONObject("activation")
        val remoteKeys = copy(empty).put("revision", "1").put("mode", "native").put("custody", "client").put("server_recovery", false)
            .put("registry_auth_version", 2).put("registry_auth", activation.getString("registry_auth"))
            .put("identity_key", activation.getJSONObject("identity_key")).put("active_key_id", activation.getString("key_id"))
            .put("keys", JSONArray().put(obj("key_id" to activation.getString("key_id"), "encrypted_bundle" to activation.getString("encrypted_bundle"))))
        SyncKeys(scope).use { keysA -> SyncKeys(scope).use { keysB ->
            keysA.restore(remoteKeys, local)
            keysB.restore(remoteKeys, null, code)
            val a = MemoryAdapter(); val b = MemoryAdapter(); val server = MemoryServer()
            val stateA = JSONObject(); val stateB = JSONObject()
            var persistedA = JSONObject(); var persistedB = JSONObject()
            fun engineA() = SyncEngine(keysA, a, server::request) { persistedA = copy(stateA) }
            fun engineB() = SyncEngine(keysB, b, server::request) { persistedB = copy(stateB) }
            a.values["fromDesktop1"] = bookmark("Desktop", "https://desktop.example.invalid/")
            b.values["fromAndroid1"] = bookmark("Android", "https://android.example.invalid/")
            server.loseNextResponse = true
            try { engineA().sync("bookmarks", stateA); fail("expected lost response") } catch (expected: SyncException) { assertEquals("network_error", expected.code) }
            assertEquals(1, persistedA.getJSONObject("outbox").length())
            val operationId = persistedA.getJSONObject("outbox").getJSONObject("fromDesktop1").getJSONObject("operation").getString("operation_id")
            engineA().sync("bookmarks", stateA)
            assertEquals(1, server.appliedOperations[operationId])
            engineB().sync("bookmarks", stateB)
            engineA().sync("bookmarks", stateA)
            assertEquals(setOf("fromDesktop1", "fromAndroid1"), a.values.keys)
            assertEquals(a.values.mapValues { canonical(it.value) }, b.values.mapValues { canonical(it.value) })
            assertTrue(server.records.values.none { it.getString("payload").contains("desktop.example") })
            a.values["fromAndroid1"]!!.put("title", "Desktop edit")
            engineA().sync("bookmarks", stateA)
            b.values["fromAndroid1"]!!.put("url", "https://edited.example.invalid/")
            engineB().sync("bookmarks", stateB)
            engineA().sync("bookmarks", stateA)
            assertEquals("Desktop edit", a.values["fromAndroid1"]!!.getString("title"))
            assertEquals("https://edited.example.invalid/", a.values["fromAndroid1"]!!.getString("url"))
            assertEquals(0, stateA.getJSONObject("conflicts").length())
            engineB().sync("bookmarks", stateB)
            a.values["fromAndroid1"]!!.put("title", "Keep this local edit")
            b.values.remove("fromAndroid1")
            engineB().sync("bookmarks", stateB)
            engineA().sync("bookmarks", stateA)
            assertEquals("Keep this local edit", a.values["fromAndroid1"]!!.getString("title"))
            assertEquals("deletion_edit", stateA.getJSONObject("conflicts").getJSONObject("fromAndroid1").getString("reason"))
            assertTrue(server.records["fromAndroid1"]!!.getBoolean("deleted"))
            val tag = copy(remoteKeys).put("revision", "2")
            try { SyncKeys(scope).use { it.restore(tag, null, code) }; fail("registry replay must be rejected") } catch (_: Exception) { }
            val record = copy(server.records.getValue("fromDesktop1")).put("deleted", true)
            try { keysA.open("bookmarks", server.generation, record); fail("metadata tampering must be rejected") } catch (_: Exception) { }
            assertTrue(persistedB.getJSONObject("inbox").length() == 0)
        } }
    }

    @Test fun history_identity_uses_the_same_utf8_json_as_desktop() {
        SyncCrypto().use { crypto ->
            val scope = "[\"http://localhost:8000/\",\"https://accounts.example.invalid/\",\"test\"]"
            val handle = crypto.call("generate", "scope" to scope)
            val compact = crypto.call("derive", "handle" to handle, "collection" to "history", "input" to "[\"https://example.invalid/á\",172800000000]")
            val escaped = crypto.call("derive", "handle" to handle, "collection" to "history", "input" to "[\"https:\\/\\/example.invalid\\/\\u00e1\", 172800000000]")
            assertEquals(compact, escaped)
        }
    }
    @Test fun new_history_parts_fill_only_the_uncovered_windows() {
        val day = 5 * 86_400_000_000L
        val quarter = 21_600_000_000L
        assertEquals(listOf(day + 2 * quarter to 2 * quarter), uncoveredHistoryWindows(day, listOf(day to day + 2 * quarter)))
        val covered = listOf(day to day + quarter, day + 3 * quarter to day + 4 * quarter)
        val gaps = uncoveredHistoryWindows(day, covered)
        assertEquals(listOf(day + quarter to quarter, day + 2 * quarter to quarter), gaps)
        val all = (covered + gaps.map { (start, length) -> start to start + length }).sortedBy { it.first }
        assertEquals(day, all.first().first)
        assertEquals(day + 4 * quarter, all.last().second)
        all.zipWithNext().forEach { (left, right) -> assertEquals(left.second, right.first) }
    }

    @Test fun account_scope_uses_canonical_server_names_and_rejects_credentials() {
        assertEquals("https://sync.example.invalid/", SyncTransport.normalize("https://SYNC.example.invalid:443/"))
        assertEquals("http://localhost:8000/", SyncTransport.normalize("http://LOCALHOST:8000"))
        for (url in listOf("https://user:secret@sync.example.invalid/", "https://sync.example.invalid/?token=x", "http://example.invalid/", "https://sync.example.invalid:0/")) {
            try { SyncTransport.normalize(url); fail("invalid server accepted") } catch (_: SyncException) { }
        }
    }

    private fun bookmark(title: String, url: String) = obj("version" to 1, "kind" to "bookmark", "parentGuid" to "mobile______", "index" to 0,
        "title" to title, "url" to url, "dateAdded" to 1700000000000L)

    private class MemoryAdapter : SyncAdapter {
        val values = linkedMapOf<String, JSONObject>()
        override suspend fun snapshot(name: String, state: JSONObject, keys: SyncKeys) = values.mapValues { copy(it.value) }
        override suspend fun read(name: String, id: String, hint: JSONObject?, state: JSONObject, keys: SyncKeys) = values[id]?.let(::copy)
        override suspend fun apply(name: String, id: String, value: JSONObject?, expected: JSONObject?, hint: JSONObject?, state: JSONObject, keys: SyncKeys, persist: () -> Unit) {
            assertTrue(same(values[id], expected)); if (value == null) values.remove(id) else values[id] = copy(value)
        }
        override suspend fun canDeleteHistory(value: JSONObject) = true
        override suspend fun clearHistoryBefore(ms: Long) = Unit
    }
    private class MemoryServer {
        val generation = UUID.randomUUID().toString()
        val records = linkedMapOf<String, JSONObject>()
        val appliedOperations = mutableMapOf<String, Int>()
        private val changes = mutableListOf<JSONObject>()
        private val receipts = mutableMapOf<String, JSONObject>()
        var loseNextResponse = false
        fun request(path: String, method: String, body: JSONObject?, query: Map<String, String>): JSONObject = when {
            path.endsWith("/changes") -> {
                val start = query["cursor"]?.toInt() ?: 0
                val page = changes.drop(start).take(1)
                obj("generation" to generation, "snapshot_sequence" to changes.size.toString(), "changes" to JSONArray(page),
                    "has_more" to (start + page.size < changes.size), "next_cursor" to (start + page.size).toString(), "compacted_through" to "0")
            }
            path.endsWith("/ack") -> JSONObject()
            else -> {
                assertEquals("POST", method)
                val operation = body!!.getJSONArray("operations").getJSONObject(0)
                val id = operation.getString("id"); val op = operation.getString("operation_id")
                val result = receipts[op] ?: run {
                    val revision = records[id]?.getString("revision") ?: "0"
                    if (operation.getString("base_revision") != revision) obj("operation_id" to op, "index" to 0, "status" to "conflict", "id" to id, "revision" to revision)
                    else {
                        val next = (revision.toInt() + 1).toString()
                        val record = obj("id" to id, "revision" to next, "deleted" to operation.getBoolean("deleted"), "payload" to operation.getString("payload"), "ttl" to null)
                        records[id] = record
                        changes.add(obj("sequence" to (changes.size + 1).toString(), "record" to copy(record)))
                        appliedOperations[op] = (appliedOperations[op] ?: 0) + 1
                        obj("operation_id" to op, "index" to 0, "status" to "applied", "id" to id, "revision" to next).also { receipts[op] = it }
                    }
                }
                if (loseNextResponse) { loseNextResponse = false; throw SyncException("network_error") }
                obj("generation" to generation, "results" to JSONArray().put(result))
            }
        }
    }
}
