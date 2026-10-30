package org.midorinext.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal interface SyncAdapter {
    suspend fun snapshot(name: String, state: JSONObject, keys: SyncKeys): Map<String, JSONObject>
    suspend fun capture(name: String, state: JSONObject, keys: SyncKeys, persist: () -> Unit,
                        consume: suspend (Map<String, JSONObject?>) -> Unit) {
        val local = snapshot(name, state, keys)
        val ids = local.keys + state.getJSONObject("mirror").keys().asSequence().toSet()
        for (batch in ids.sorted().chunked(100)) consume(batch.associateWith { local[it] })
    }
    suspend fun read(name: String, id: String, hint: JSONObject?, state: JSONObject, keys: SyncKeys): JSONObject?
    suspend fun apply(name: String, id: String, value: JSONObject?, expected: JSONObject?, hint: JSONObject?, state: JSONObject, keys: SyncKeys, persist: () -> Unit)
    suspend fun markPending(name: String, id: String, hint: JSONObject?, state: JSONObject) {}
    suspend fun canDeleteHistory(value: JSONObject): Boolean
    suspend fun canDelete(name: String, id: String, value: JSONObject, state: JSONObject): Boolean = name != "history" || canDeleteHistory(value)
    suspend fun clearHistoryBefore(ms: Long)
}

internal class SyncEngine(
    private val keys: SyncKeys,
    private val stores: SyncAdapter,
    private val request: (String, String, JSONObject?, Map<String, String>) -> JSONObject,
    private val metrics: SyncRunMetrics? = null,
    private val persist: () -> Unit,
) {
    suspend fun sync(name: String, state: JSONObject) {
        ensureState(state)
        try { upload(name, state) } catch (error: SyncException) {
            if (error.code != "reset_required") throw error
        }
        for (pageNumber in 0 until 1000) {
            val query = mutableMapOf("limit" to "100", "max_bytes" to "2097152")
            state.textOrNull("cursor")?.let { query["cursor"] = it }
            val page = try { request(path(name, "changes"), "GET", null, query) }
            catch (error: SyncException) {
                if (error.code !in listOf("reset_required", "invalid_cursor")) throw error
                request(path(name, "changes"), "GET", null, mapOf("limit" to "100", "max_bytes" to "2097152"))
            }
            val generation = page.getString("generation")
            requireSync(generation.matches(UUID_PATTERN), "invalid_change_page")
            if (state.textOrNull("generation") != generation) {
                if (state.textOrNull("generation") != null || page.objectOrNull("control") != null) reset(name, state, page)
                state.put("generation", generation).put("sequence", "0").put("compacted", "0").put("fence", JSONObject.NULL).put("cursor", JSONObject.NULL)
                persist()
            }
            validatePage(page, state)
            metrics?.receivedRecords?.addAndGet(page.objects("changes").size.toLong())
            for (change in page.objects("changes")) {
                val record = change.getJSONObject("record")
                try {
                    validateRecord(record)
                    state.getJSONObject("inbox").put(record.getString("id"), copy(record))
                } catch (error: Exception) {
                    val id = record.textOrNull("id")?.takeIf { it.length in 1..255 && it.none(Char::isISOControl) }
                        ?: "invalid:" + change.getString("sequence")
                    state.getJSONObject("conflicts").put(id, obj("reason" to "invalid_record", "record" to record))
                }
            }
            state.put("cursor", page.getString("next_cursor"))
                .put("sequence", page.objects("changes").lastOrNull()?.getString("sequence") ?: if (page.optBoolean("snapshot_rebased")) page.getString("compacted_through") else state.getString("sequence"))
                .put("compacted", page.optString("compacted_through", "0"))
                .put("fence", if (page.getBoolean("has_more")) page.getString("snapshot_sequence") else JSONObject.NULL)
            persist()
            applyInbox(name, state)
            if (!page.getBoolean("has_more")) {
                if (state.optString("ack_sequence", "") != state.getString("sequence")) {
                    request(path(name, "ack"), "POST", obj("cursor" to state.getString("cursor")), emptyMap())
                    state.put("ack_sequence", state.getString("sequence")); persist()
                }
                break
            }
            requireSync(pageNumber < 999, "sync_page_limit")
        }
        capture(name, state)
        upload(name, state)
    }
    private fun ensureState(state: JSONObject) {
        listOf("mirror", "inbox", "outbox", "conflicts", "links", "projections").forEach { if (!state.has(it)) state.put(it, JSONObject()) }
        if (!state.has("sequence")) state.put("sequence", "0")
    }
    private suspend fun applyInbox(name: String, state: JSONObject) {
        val inbox = state.getJSONObject("inbox")
        val mirror = state.getJSONObject("mirror")
        val conflicts = state.getJSONObject("conflicts")
        repeat(4) { attempt ->
            for ((id, untyped) in inbox.entries()) {
                val record = untyped as JSONObject
                try {
                    val old = mirror.objectOrNull(id)
                    if (old != null && record.getString("revision").toLong() <= old.getString("revision").toLong()) {
                        requireSync(record.getString("revision") != old.getString("revision") || same(record, old.objectOrNull("record")), "record_replay_mismatch")
                        inbox.remove(id); persist(); continue
                    }
                    val remote = keys.open(name, state.getString("generation"), record)?.let { SyncModels.validate(keys, name, id, it) }
                    val base = old?.objectOrNull("value")
                    val intent = state.objectOrNull("application")?.takeIf { it.getString("id") == id }
                    val local = stores.read(name, id, remote ?: base, state, keys)
                    val decision = if (intent != null && same(local, intent.objectOrNull("desired"))) obj("status" to "keep_local")
                        else SyncModels.merge(keys, name, id, base, local, remote)
                    if (decision.getString("status") == "conflict") {
                        conflicts.put(id, obj("reason" to decision.getString("reason"), "record" to record, "base" to base, "local" to local, "remote" to remote))
                        inbox.remove(id); state.remove("application"); persist(); continue
                    }
                    val desired = if (decision.getString("status") == "apply") decision.objectOrNull("value") else local
                    if (!same(local, desired)) {
                        state.put("application", obj("id" to id, "desired" to desired, "before" to local, "record" to record))
                        persist()
                        stores.apply(name, id, desired, local, remote ?: base, state, keys, persist)
                    }
                    mirror.put(id, obj("revision" to record.getString("revision"), "value" to remote, "record" to record))
                    if (!same(desired, remote)) stores.markPending(name, id, desired ?: remote ?: base, state)
                    inbox.remove(id); conflicts.remove(id); state.remove("application"); persist()
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    val code = (error as? SyncException)?.code ?: "invalid_record"
                    if (code in listOf("parent_missing", "folder_not_empty")) continue
                    if (code in listOf("local_changed", "native_write_pending")) continue
                    conflicts.put(id, obj("reason" to code, "record" to record))
                    inbox.remove(id); state.remove("application"); persist()
                }
            }
            if (inbox.length() == 0) return
        }
    }
    private suspend fun capture(name: String, state: JSONObject) {
        val mirror = state.getJSONObject("mirror")
        val outbox = state.getJSONObject("outbox")
        val conflicts = state.getJSONObject("conflicts")
        stores.capture(name, state, keys, persist) { local ->
            var changed = false
            for ((id, value) in local) {
                metrics?.prepared?.incrementAndGet()
                if (outbox.has(id) || conflicts.has(id) || state.getJSONObject("inbox").has(id)) continue
                val base = mirror.objectOrNull(id)
                if (same(value, base?.objectOrNull("value"))) continue
                if (value == null && base?.objectOrNull("value")?.let { stores.canDelete(name, id, it, state) } == false) continue
                try {
                    if (value != null) SyncModels.validate(keys, name, id, value)
                    val revision = base?.getString("revision") ?: "0"
                    val operation = obj("operation_id" to UUID.randomUUID().toString(), "id" to id, "base_revision" to revision,
                        "payload" to keys.seal(name, id, state.getString("generation"), revision, value), "deleted" to (value == null), "ttl" to null)
                    outbox.put(id, obj("operation" to operation, "value" to value, "crypto" to keys.context))
                    changed = true
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    conflicts.put(id, obj("reason" to ((error as? SyncException)?.code ?: "invalid_local_record")))
                    changed = true
                }
            }
            if (changed) { persist(); upload(name, state) }
        }
    }
    private fun upload(name: String, state: JSONObject) {
        val outbox = state.getJSONObject("outbox")
        if (outbox.length() == 0 || !state.has("generation")) return
        while (outbox.length() > 0) {
            val candidates = mutableListOf<Pair<String, JSONObject>>()
            var bytes = 0
            var crypto: JSONObject? = null
            for ((id, untyped) in outbox.entries()) {
                val entry = untyped as JSONObject
                if (crypto != null && !same(crypto, entry.getJSONObject("crypto"))) continue
                val size = entry.getJSONObject("operation").toString().toByteArray().size
                if (candidates.isNotEmpty() && (candidates.size >= 100 || bytes + size > 3 * 1024 * 1024)) break
                crypto = entry.getJSONObject("crypto")
                candidates.add(id to entry); bytes += size + 1
            }
            val response = request(path(name, "operations"), "POST", obj("generation" to state.getString("generation"),
                "crypto" to crypto, "operations" to JSONArray(candidates.map { it.second.getJSONObject("operation") })), emptyMap())
            requireSync(response.getString("generation") == state.getString("generation"), "invalid_operation_results")
            val results = response.getJSONArray("results")
            requireSync(results.length() == candidates.size, "invalid_operation_results")
            // Validate the complete response before advancing any durable receipts.
            for ((index, candidate) in candidates.withIndex()) {
                val (id, entry) = candidate
                val operation = entry.getJSONObject("operation")
                val result = results.getJSONObject(index)
                requireSync(result.getInt("index") == index && result.getString("operation_id") == operation.getString("operation_id") &&
                    result.getString("status") in listOf("applied", "conflict", "quota_exceeded", "invalid", "idempotency_conflict"), "invalid_operation_results")
                if (result.getString("status") == "applied") requireSync(result.getString("id") == id &&
                    result.getString("revision").toLong() == operation.getString("base_revision").toLong() + 1, "invalid_operation_results")
            }
            var quota = false
            for ((index, candidate) in candidates.withIndex()) {
                val (id, entry) = candidate
                val operation = entry.getJSONObject("operation")
                val result = results.getJSONObject(index)
                when (result.getString("status")) {
                    "applied" -> {
                        val record = obj("id" to id, "revision" to result.getString("revision"), "payload" to operation.getString("payload"), "deleted" to operation.getBoolean("deleted"), "ttl" to operation.opt("ttl"))
                        state.getJSONObject("mirror").put(id, obj("revision" to result.getString("revision"), "value" to entry.opt("value"), "record" to record))
                        outbox.remove(id); metrics?.uploaded?.incrementAndGet()
                    }
                    "conflict" -> outbox.remove(id)
                    "quota_exceeded" -> quota = true
                    else -> {
                        state.getJSONObject("conflicts").put(id, obj("reason" to result.getString("status"), "pending" to entry))
                        outbox.remove(id)
                    }
                }
            }
            persist()
            if (quota) throw SyncException("quota_exceeded")
        }
    }

    private suspend fun reset(name: String, state: JSONObject, page: JSONObject) {
        val control = page.objectOrNull("control") ?: throw SyncException("reset_requires_review")
        val generation = page.getString("generation")
        val input = JSONArray().put("MidoriSync/control/v1").put(control.getString("epoch")).put(name).put(control.getString("operation_id"))
            .put(control.getString("previous_generation")).put(generation).put(control.getString("control_revision"))
            .put(control.getString("kind")).put(control.getLong("clear_before_ms"))
        requireSync(control.getInt("version") == 1 && control.getString("epoch") == keys.context.getString("epoch") && control.getString("collection") == name &&
            control.getString("generation") == generation && control.getString("control_revision").toLong() >= state.optString("control_revision", "0").toLong() &&
            control.getString("proof") == keys.derive("devices", input), "control_authentication_failed")
        requireSync(name == "history" && control.getString("kind") == "history_clear", "reset_requires_review")
        state.put("reset", copy(control)); persist()
        stores.clearHistoryBefore(control.getLong("clear_before_ms"))
        state.put("mirror", JSONObject()).put("inbox", JSONObject()).put("outbox", JSONObject()).put("conflicts", JSONObject())
            .put("control_revision", control.getString("control_revision"))
        state.remove("reset"); state.remove("application"); state.remove("ack_sequence")
        listOf("native_epoch", "native_revision", "native_local_revision", "native_identity").forEach(state::remove)
        persist()
    }
    private fun validatePage(page: JSONObject, state: JSONObject) {
        val sequence = state.getString("sequence").toLong()
        val snapshot = counter(page.getString("snapshot_sequence"))
        val floor = counter(page.optString("compacted_through", "0"))
        val rebased = page.optBoolean("snapshot_rebased")
        requireSync(page.getString("next_cursor").length in 1..4096 && floor >= state.optString("compacted", "0").toLong() && floor <= snapshot && snapshot >= sequence &&
            (rebased || state.textOrNull("fence") == null || state.getString("fence") == snapshot.toString()), "invalid_change_page")
        var current = if (rebased) floor else sequence
        val changes = page.objects("changes")
        requireSync(changes.size <= 100 && (!rebased || floor >= sequence), "invalid_change_page")
        for (change in changes) { val next = counter(change.getString("sequence")); requireSync(next > current && next <= snapshot, "invalid_change_page"); current = next }
        requireSync(if (page.getBoolean("has_more")) changes.isNotEmpty() && current < snapshot else current == snapshot, "invalid_change_page")
    }
    private fun validateRecord(record: JSONObject) {
        val id = record.getString("id")
        requireSync(id.isNotEmpty() && id.toByteArray().size <= 255 && id.none(Char::isISOControl) && counter(record.getString("revision")) in 1..Int.MAX_VALUE.toLong() &&
            record.opt("deleted") is Boolean && record.getString("payload").toByteArray().size in 1..262144 && (record.isNull("ttl") || record.opt("ttl") is String))
    }
    private fun counter(value: String): Long { requireSync(value.matches(Regex("0|[1-9][0-9]{0,18}")), "invalid_counter"); return value.toLongOrNull() ?: throw SyncException("invalid_counter") }
    private fun path(name: String, suffix: String) = "api/v1/sync/collections/$name/$suffix"
}
