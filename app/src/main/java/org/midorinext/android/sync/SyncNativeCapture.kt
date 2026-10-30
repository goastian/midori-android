package org.midorinext.android.sync

import androidx.room.withTransaction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.midorinext.android.storage.bookmarks.BookmarkNode
import mozilla.components.concept.storage.BookmarkNodeType

internal suspend fun SyncNativeStores.nativeScope(name: String, state: JSONObject, keys: SyncKeys): String {
    prepareJournals()
    val scope = keys.storageScope + if (name == "history") ":${keys.state.getString("epoch")}:${keys.state.getJSONObject("identity_key").getString("key_id")}" else ""
    state.put("native_scope", scope)
    return scope
}

internal suspend fun SyncNativeStores.nativeSnapshot(name: String, state: JSONObject, keys: SyncKeys): Map<String, JSONObject> {
    nativeScope(name, state, keys)
    val result = linkedMapOf<String, JSONObject>()
    fullNative(name, state, keys) { batch -> batch.forEach { (id, value) -> if (value != null) result[id] = value } }
    return result
}

internal suspend fun SyncNativeStores.captureNative(
    name: String, state: JSONObject, keys: SyncKeys, persist: () -> Unit,
    consume: suspend (Map<String, JSONObject?>) -> Unit
) {
    nativeScope(name, state, keys)
    val db = if (name == "history") history else bookmarks
    val journal = if (name == "history") history.syncJournalDao() else bookmarks.syncJournalDao()
    val current = journal.revision()
    val identity = state.getString("native_scope")
    val bootstrap = state.optString("native_epoch") != current.epoch || state.optString("native_identity") != identity
    if (!bootstrap && state.optLong("native_local_revision", -1) == current.localRevision) return
    var cursor = state.optLong("native_revision", 0)
    if (bootstrap) {
        fullNative(name, state, keys, consume)
        val pending = linkedMapOf<String, JSONObject?>()
        val mirror = state.getJSONObject("mirror")
        for (id in mirror.keys()) {
            currentCoroutineContext().ensureActive()
            val hint = mirror.optJSONObject(id)?.objectOrNull("value")
            pending[id] = read(name, id, hint, state, keys)
            if (pending.size >= if (name == "history") 16 else 100) { consume(pending.toMap()); pending.clear() }
        }
        if (pending.isNotEmpty()) consume(pending)
        cursor = current.revision
    } else {
        while (true) {
            currentCoroutineContext().ensureActive()
            val changes = journal.changes(cursor, current.revision, 100)
            if (changes.isEmpty()) break
            for (change in changes) {
                when {
                    name == "bookmarks" && change.kind == "node" -> {
                        val value = bookmarks.withTransaction { bookmarks.bookmarksDao().get(change.key)?.let { bookmarkIdentity(it, state).let { id -> id to bookmarkValue(it, state, knownId = id) } } }
                        if (value != null) consume(mapOf(value))
                        else {
                            val ids = journal.identities(identity, change.key).map { it.id }
                            consume(ids.associateWith { null })
                        }
                    }
                    name == "bookmarks" && change.kind == "folder" -> {
                        var after = ""
                        while (true) {
                            val nodes = bookmarks.bookmarksDao().syncNullPositionPage(change.key, after, 100)
                            if (nodes.isEmpty()) break
                            val values = db.withTransaction { nodes.associate { bookmarkIdentity(it, state).let { id -> id to bookmarkValue(it, state, knownId = id) } } }
                            consume(values)
                            after = nodes.last().guid
                        }
                    }
                    name == "history" && change.kind == "day" -> {
                        val separator = change.key.lastIndexOf('\u001f')
                        historyUrl(change.key.substring(0, separator), state, keys, consume, change.key.substring(separator + 1).toLong())
                    }
                    name == "history" && change.kind == "url" -> historyUrl(change.key, state, keys, consume)
                    name == "history" && change.kind == "removal" -> {
                        val removal = history.historyDao().syncRemoval(change.key.toLong()) ?: continue
                        if (removal.url != null) historyUrl(removal.url, state, keys, consume)
                        else {
                            val pending = linkedMapOf<String, JSONObject?>()
                            for (id in state.getJSONObject("mirror").keys()) {
                                val hint = state.getJSONObject("mirror").optJSONObject(id)?.objectOrNull("value") ?: continue
                                val (start, end) = window(hint)
                                if (start / 1000 <= removal.endTime && (end - 1) / 1000 >= removal.startTime) {
                                    pending[id] = historyValue(hint, state, id = id)
                                    if (pending.size >= if (name == "history") 16 else 100) { consume(pending.toMap()); pending.clear() }
                                }
                            }
                            if (pending.isNotEmpty()) consume(pending)
                        }
                    }
                }
            }
            cursor = changes.last().localRevision
            state.put("native_epoch", current.epoch).put("native_identity", identity).put("native_revision", cursor)
            persist()
        }
        cursor = current.revision
    }
    state.put("native_epoch", current.epoch).put("native_identity", identity)
        .put("native_revision", cursor).put("native_local_revision", current.localRevision)
    persist()
    journal.compact(cursor)
}

private suspend fun SyncNativeStores.fullNative(
    name: String, state: JSONObject, keys: SyncKeys,
    consume: suspend (Map<String, JSONObject?>) -> Unit
) {
    var after = ""
    while (true) {
        currentCoroutineContext().ensureActive()
        if (name == "bookmarks") {
            val values = bookmarks.withTransaction {
                val nodes = bookmarks.bookmarksDao().syncNodePage(after, 100)
                if (nodes.isNotEmpty()) after = nodes.last().guid
                nodes to nodes.filter { it.guid !in SyncNativeStores.ROOTS }.associate { node ->
                    val id = bookmarkIdentity(node, state)
                    id to bookmarkValue(node, state, knownId = id)
                }
            }
            if (values.first.isEmpty()) break
            consume(values.second)
        } else {
            val urls = history.historyDao().syncUrlPage(after, 100)
            if (urls.isEmpty()) break
            for (url in urls) historyUrl(url, state, keys, consume)
            after = urls.last()
        }
    }
}

internal suspend fun SyncNativeStores.bookmarkIdentity(node: BookmarkNode, state: JSONObject): String {
    val scope = state.getString("native_scope")
    val known = bookmarks.syncJournalDao().identities(scope, node.guid).firstOrNull()?.id
    val id = known ?: wireId(state, node.guid)
    links(state).put(id, node.guid)
    if (known == null) bookmarks.syncJournalDao().remember(SyncRecordIndex(scope, id, node.guid))
    return id
}

internal suspend fun SyncNativeStores.bookmarkValue(node: BookmarkNode, state: JSONObject, raw: Boolean = false, knownId: String? = null): JSONObject? {
    if (node.type == BookmarkNodeType.ITEM && !SyncModels.http(node.url.orEmpty())) return null
    val id = knownId ?: bookmarkIdentity(node, state)
    val parent = node.parentGuid
    val value = obj("version" to 1, "kind" to when (node.type) {
        BookmarkNodeType.FOLDER -> "folder"; BookmarkNodeType.SEPARATOR -> "separator"; else -> "bookmark"
    }, "parentGuid" to if (parent == SyncNativeStores.LOCAL_ROOT || parent == null) "mobile______" else wireId(state, parent),
        "index" to (node.position ?: bookmarks.bookmarksDao().syncFallbackIndex(parent, node.dateAdded, node.guid)),
        "title" to node.title.orEmpty(), "url" to node.url, "dateAdded" to node.dateAdded)
    return if (raw) value else projected(state, id, value)
}

internal suspend fun SyncNativeStores.indexedNativeId(name: String, id: String, state: JSONObject): String {
    links(state).textOrNull(id)?.let { return it }
    val journal = if (name == "history") history.syncJournalDao() else bookmarks.syncJournalDao()
    return journal.identity(state.getString("native_scope"), id)?.nativeKey ?: id
}

private suspend fun SyncNativeStores.historyUrl(
    url: String, state: JSONObject, keys: SyncKeys, consume: suspend (Map<String, JSONObject?>) -> Unit, onlyDay: Long? = null
) {
    if (!SyncModels.http(url)) return
    val journal = history.syncJournalDao()
    val scope = state.getString("native_scope")
    val values = linkedMapOf<String, JSONObject?>()
    var knownAfter = ""
    while (true) {
        val known = if (onlyDay == null) journal.identityPage(scope, url, knownAfter, 16) else journal.dayIdentityPage(scope, url, onlyDay, knownAfter, 16)
        if (known.isEmpty()) break
        history.withTransaction {
            val title = history.historyDao().getPage(url)?.title.orEmpty()
            for (entry in known) {
                val hint = state.getJSONObject("mirror").optJSONObject(entry.id)?.objectOrNull("value") ?: identity(entry)
                values[entry.id] = historyValue(hint, state, id = entry.id, pageTitle = title)
            }
        }
        consume(values.toMap()); values.clear()
        knownAfter = known.last().id
    }
    var after = 0L
    while (true) {
        val days = if (onlyDay == null) history.historyDao().syncDayPage(url, after, 100) else if (after <= onlyDay) listOf(onlyDay) else emptyList()
        if (days.isEmpty()) break
        for (day in days) {
            val windows = journal.windows(scope, url, day)
            val spans = windows.map { it.windowStart to it.windowEnd }
            val gaps = if (spans.isEmpty()) listOf(day to SyncNativeStores.DAY) else uncoveredHistoryWindows(day, spans)
            for ((start, length) in gaps) {
                splitWindow(url, day, start, length, state, keys) { id, value ->
                    values[id] = value
                    if (values.size >= 16) { consume(values.toMap()); values.clear() }
                }
            }
        }
        after = days.last() + SyncNativeStores.DAY
    }
    if (values.isNotEmpty()) consume(values)
}

private fun identity(entry: SyncRecordIndex) = obj("version" to 1, "url" to entry.nativeKey, "dayStartUsec" to entry.dayStart).also {
    if (entry.windowStart != entry.dayStart || entry.windowEnd != entry.dayStart + SyncNativeStores.DAY) {
        it.put("part", obj("startUsec" to entry.windowStart, "lenUsec" to entry.windowEnd - entry.windowStart))
    }
}

private suspend fun SyncNativeStores.splitWindow(
    url: String, day: Long, start: Long, length: Long, state: JSONObject, keys: SyncKeys,
    consume: suspend (String, JSONObject) -> Unit
) {
    val count = history.historyDao().syncWindowCount(url, start, start + length)
    if (count == 0) return
    if (count > 2048) {
        requireSync(length > SyncNativeStores.DAY / 8192, "history_record_too_large")
        val half = length / 2
        splitWindow(url, day, start, half, state, keys, consume)
        splitWindow(url, day, start + half, length - half, state, keys, consume)
        return
    }
    val hint = obj("version" to 1, "url" to url, "dayStartUsec" to day)
    if (start != day || length != SyncNativeStores.DAY) hint.put("part", obj("startUsec" to start, "lenUsec" to length))
    val id = SyncModels.historyId(keys, hint)
    history.syncJournalDao().remember(SyncRecordIndex(state.getString("native_scope"), id, url, day, start, start + length))
    historyValue(hint, state, id = id)?.let { consume(id, it) }
}
