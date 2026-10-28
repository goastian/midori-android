package org.midorinext.android.sync

import androidx.room.withTransaction
import mozilla.components.concept.storage.*
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import org.json.JSONArray
import org.json.JSONObject
import org.midorinext.android.storage.autofill.LoginAutofillRepository
import org.midorinext.android.storage.bookmarks.BookmarkNode
import org.midorinext.android.storage.bookmarks.BookmarksDatabase
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.Page
import org.midorinext.android.storage.history.Visit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SyncNativeStores @Inject constructor(
    private val bookmarks: BookmarksDatabase,
    private val history: HistoryDatabase,
    private val logins: LoginAutofillRepository,
    private val autofill: dagger.Lazy<AutofillCreditCardsAddressesStorage>,
    private val forms: SyncFormHistory,
) : SyncAdapter {
    private fun links(state: JSONObject) = state.objectOrNull("links") ?: JSONObject().also { state.put("links", it) }
    private fun projections(state: JSONObject) = state.objectOrNull("projections") ?: JSONObject().also { state.put("projections", it) }
    private fun nativeId(state: JSONObject, id: String) = links(state).optString(id, id)
    private fun wireId(state: JSONObject, id: String) = links(state).entries().firstOrNull { it.second == id }?.first ?: portableGuid(id)
    private fun wireIds(state: JSONObject, native: String, password: Boolean = false): List<String> {
        val existing = links(state).entries().filter { it.second == native }.map { it.first }.sorted()
        if (existing.isNotEmpty()) return existing
        val id = if (password) native else portableGuid(native)
        links(state).put(id, native)
        return listOf(id)
    }
    private fun adopt(state: JSONObject, id: String, native: String) {
        for ((other, value) in links(state).entries()) {
            if (value == native && other != id && state.optJSONObject("mirror")?.has(other) != true &&
                state.optJSONObject("outbox")?.has(other) != true) {
                links(state).remove(other); projections(state).remove(other)
            }
        }
        links(state).put(id, native)
    }
    private fun projected(state: JSONObject, id: String, raw: JSONObject): JSONObject {
        val previous = projections(state).objectOrNull(id) ?: return raw
        val result = copy(previous.getJSONObject("value"))
        val native = previous.getJSONObject("native")
        for ((key, value) in raw.entries()) if (!same(value, native.opt(key))) result.put(key, value)
        return result
    }
    private fun shadow(state: JSONObject, id: String, raw: JSONObject, value: JSONObject) {
        projections(state).put(id, obj("native" to raw, "value" to value))
    }
    override suspend fun snapshot(name: String, state: JSONObject, keys: SyncKeys): Map<String, JSONObject> = when (name) {
        "bookmarks" -> bookmarkSnapshot(state)
        "history" -> historySnapshot(state, keys)
        "passwords" -> logins.list().filter { SyncModels.http(it.origin) }.flatMap { login ->
            wireIds(state, login.guid, password = true).map { id -> id to projected(state, id, loginValue(login)) }
        }.toMap()
        "credit-cards" -> {
            val storage = autofill.get(); val crypto = storage.getCreditCardCrypto(); val key = crypto.getOrGenerateKey()
            storage.getAllCreditCards().flatMap { card ->
                val number = crypto.decrypt(key, card.encryptedCardNumber)?.number ?: throw SyncException("cards_locked")
                wireIds(state, card.guid).map { id -> id to projected(state, id, cardValue(card, number)) }
            }.toMap()
        }
        "addresses" -> autofill.get().getAllAddresses().flatMap { address ->
            wireIds(state, address.guid).map { id -> id to projected(state, id, addressValue(address)) }
        }.toMap()
        "form-history" -> {
            val rows = forms.request("list") as JSONArray
            val result = linkedMapOf<String, JSONObject>()
            for (index in 0 until rows.length()) {
                val row = copy(rows.getJSONObject(index)).apply { remove("guid"); put("version", 1) }
                val id = "f1:" + keys.derive(name, JSONArray().put(row.getString("fieldname")).put(row.getString("value")))
                val old = result[id]
                result[id] = if (old == null) row else copy(row).put("firstUsed", minOf(old.getLong("firstUsed"), row.getLong("firstUsed")))
                    .put("lastUsed", maxOf(old.getLong("lastUsed"), row.getLong("lastUsed"))).put("timesUsed", maxOf(old.getLong("timesUsed"), row.getLong("timesUsed")))
            }
            result
        }
        else -> throw SyncException("unsupported_collection")
    }
    override suspend fun read(name: String, id: String, hint: JSONObject?, state: JSONObject, keys: SyncKeys): JSONObject? {
        if (name == "history" && hint != null) return historyValue(hint, state)
        return snapshot(name, state, keys)[id]
    }
    override suspend fun apply(name: String, id: String, value: JSONObject?, expected: JSONObject?, hint: JSONObject?, state: JSONObject, keys: SyncKeys, persist: () -> Unit) {
        requireSync(same(read(name, id, hint ?: value ?: expected, state, keys), expected), "local_changed")
        if (value == null && name in listOf("passwords", "credit-cards", "addresses")) {
            val native = nativeId(state, id)
            val shared = links(state).entries().any { (other, target) -> other != id && target == native &&
                (state.optJSONObject("mirror")?.optJSONObject(other)?.objectOrNull("value") != null ||
                    state.optJSONObject("outbox")?.optJSONObject(other)?.objectOrNull("value") != null) }
            if (shared) {
                links(state).remove(id); projections(state).remove(id); persist()
                return
            }
        }
        when (name) {
            "bookmarks" -> bookmarks.withTransaction {
                requireSync(same(bookmarkSnapshot(state)[id], expected), "local_changed")
                val dao = bookmarks.bookmarksDao()
                val localId = nativeId(state, id)
                if (value == null) {
                    requireSync(dao.getChildren(localId).isEmpty(), "folder_not_empty")
                    dao.deleteByGuid(localId)
                } else {
                    val parent = nativeBookmarkParent(value.getString("parentGuid"))
                    if (parent in ROOTS && dao.get(parent) == null) dao.syncUpsert(BookmarkNode(parent, BookmarkNodeType.FOLDER, LOCAL_ROOT,
                        ROOTS.indexOf(parent), rootTitle(parent), null, 0))
                    requireSync(parent == LOCAL_ROOT || dao.get(parent)?.type == BookmarkNodeType.FOLDER, "parent_missing")
                    var ancestor: String? = parent
                    val seen = mutableSetOf(localId)
                    while (ancestor != null && ancestor != LOCAL_ROOT) {
                        requireSync(seen.add(ancestor), "bookmark_cycle")
                        ancestor = dao.get(ancestor)?.parentGuid
                    }
                    dao.syncUpsert(BookmarkNode(localId, when(value.getString("kind")) {
                        "folder" -> BookmarkNodeType.FOLDER; "separator" -> BookmarkNodeType.SEPARATOR; else -> BookmarkNodeType.ITEM
                    }, parent, value.getInt("index"), value.getString("title"), value.textOrNull("url"), value.getLong("dateAdded")))
                    links(state).put(id, localId)
                    shadow(state, id, bookmarkSnapshot(state, raw = true).getValue(id), value)
                }
            }
            "history" -> history.withTransaction {
                val identity = value ?: hint ?: expected ?: return@withTransaction
                requireSync(same(historyValue(identity, state), expected), "local_changed")
                val (start, end) = window(identity)
                val url = identity.getString("url")
                val dao = history.historyDao()
                dao.deleteSyncWindow(url, start, end)
                if (value != null) {
                    dao.upsert(Page(url, value.getString("title"), dao.getPage(url)?.previewImageUrl))
                    value.objects("visits").forEach { visit ->
                        val usec = visit.getLong("atUsec")
                        dao.insert(Visit(url, usec / 1000, visitType(visit.getInt("transition")), usec, visit.getInt("count")))
                    }
                    shadow(state, id, historyValue(value, state, raw = true) ?: copy(value), value)
                } else dao.prunePages()
            }
            "passwords" -> logins.syncTransaction {
                val currentId = nativeId(state, id)
                if (value == null) {
                    delete(currentId)
                } else {
                    val entry = LoginEntry(value.getString("origin"), value.textOrNull("formActionOrigin"), value.textOrNull("httpRealm"),
                        value.getString("usernameField"), value.getString("passwordField"), value.getString("username"), value.getString("password"))
                    val existing = get(currentId) ?: list().find { it.origin == entry.origin && it.formActionOrigin == entry.formActionOrigin && it.httpRealm == entry.httpRealm && it.username == entry.username }
                    requireSync(existing == null || expected != null || existing.password == entry.password, "password_duplicate_credential")
                    val saved = if (existing == null) add(entry) else if (existing.toEntry() == entry) existing else update(existing.guid, entry)
                    adopt(state, id, saved.guid)
                    shadow(state, id, loginValue(saved), value)
                }
            }
            "credit-cards" -> {
                val storage = autofill.get()
                if (value == null) storage.deleteCreditCard(nativeId(state, id)) else {
                    val number = value.getString("number")
                    val crypto = storage.getCreditCardCrypto(); val key = crypto.getOrGenerateKey()
                    val existing = storage.getCreditCard(nativeId(state, id)) ?: storage.getAllCreditCards().find { card ->
                        crypto.decrypt(key, card.encryptedCardNumber)?.number == number
                    }
                    if (existing != null && expected == null) requireSync(same(cardValue(existing, number), value), "card_duplicate")
                    val saved = if (existing == null) storage.addCreditCard(NewCreditCardFields(value.getString("name"), CreditCardNumber.Plaintext(number), number.takeLast(4), value.optLong("month"), value.optLong("year"), ""))
                    else {
                        storage.updateCreditCard(existing.guid, UpdatableCreditCardFields(value.getString("name"), CreditCardNumber.Plaintext(number), number.takeLast(4), value.optLong("month"), value.optLong("year"), existing.cardType))
                        storage.getCreditCard(existing.guid)!!
                    }
                    adopt(state, id, saved.guid)
                    shadow(state, id, cardValue(saved, number), value)
                }
            }
            "addresses" -> {
                val storage = autofill.get()
                if (value == null) storage.deleteAddress(nativeId(state, id)) else {
                    val fields = value.getJSONObject("fields")
                    val f = { key: String -> fields.optString(key, "") }
                    val entry = UpdatableAddressFields(f("name"), f("organization"), f("street-address"), f("address-level3"), f("address-level2"), f("address-level1"), f("postal-code"), f("country"), f("tel"), f("email"))
                    val existing = storage.getAddress(nativeId(state, id)) ?: storage.getAllAddresses().find { address ->
                        val actual = addressValue(address).getJSONObject("fields")
                        SyncModels.addressFields.all { actual.optString(it) == fields.optString(it) }
                    }
                    val saved = if (existing == null) storage.addAddress(entry) else if (SyncModels.addressFields.all {
                        addressValue(existing).getJSONObject("fields").optString(it) == fields.optString(it)
                    }) existing else {
                        storage.updateAddress(existing.guid, entry); storage.getAddress(existing.guid)!!
                    }
                    adopt(state, id, saved.guid)
                    shadow(state, id, addressValue(saved), value)
                }
            }
            "form-history" -> {
                val identity = value ?: hint ?: expected ?: return
                forms.request("apply", "identity" to identity, "value" to value)
            }
        }
        if (value == null) projections(state).remove(id)
        persist()
        requireSync(same(read(name, id, value ?: hint ?: expected, state, keys), value), "native_write_pending")
    }
    private suspend fun bookmarkSnapshot(state: JSONObject, raw: Boolean = false): Map<String, JSONObject> {
        val nodes = bookmarks.bookmarksDao().syncNodes().filter { it.guid !in ROOTS }
        val result = linkedMapOf<String, JSONObject>()
        for ((parent, children) in nodes.groupBy { it.parentGuid }) for ((index, node) in children.withIndex()) {
            val id = wireId(state, node.guid)
            links(state).put(id, node.guid)
            val value = obj("version" to 1, "kind" to when(node.type) { BookmarkNodeType.FOLDER -> "folder"; BookmarkNodeType.SEPARATOR -> "separator"; else -> "bookmark" },
                "parentGuid" to if (parent == LOCAL_ROOT || parent == null) "mobile______" else wireId(state, parent),
                "index" to (node.position ?: index), "title" to (node.title ?: ""), "url" to node.url, "dateAdded" to node.dateAdded)
            if (node.type != BookmarkNodeType.ITEM || SyncModels.http(node.url.orEmpty())) result[id] = if (raw) value else projected(state, id, value)
        }
        return result
    }
    private suspend fun historySnapshot(state: JSONObject, keys: SyncKeys): Map<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()
        val visits = history.historyDao().syncVisits().filter { SyncModels.http(it.pageUri) }
        val known = state.objectOrNull("mirror")?.entries().orEmpty().mapNotNull { (it.second as? JSONObject)?.objectOrNull("value") }
        val identities = known.associateBy { it.getString("url") to it.getLong("dayStartUsec") }
        for ((identity, group) in visits.groupBy { it.pageUri to it.timestampUsec / DAY * DAY }) {
            val parts = known.filter { it.optString("url") == identity.first && it.optLong("dayStartUsec") == identity.second && it.has("part") }
            if (parts.isNotEmpty()) {
                parts.forEach { value -> historyValue(value, state)?.let { result[SyncModels.historyId(keys, value)] = it } }
                val remaining = group.filter { visit -> parts.none { value -> val (start, end) = window(value); visit.timestampUsec in start until end } }
                if (remaining.isNotEmpty()) {
                    for ((start, length) in uncoveredHistoryWindows(identity.second, parts.map(::window))) {
                        splitHistory(identity.first, identity.second, start, length, remaining.filter { it.timestampUsec in start until start + length }, state, keys, result, partitioned = true)
                    }
                }
            } else {
                val knownValue = identities[identity]
                if (knownValue != null && group.size <= 2048) historyValue(knownValue, state)?.let { result[SyncModels.historyId(keys, knownValue)] = it }
                else splitHistory(identity.first, identity.second, identity.second, DAY, group, state, keys, result)
            }
        }
        for (value in known) {
            val id = SyncModels.historyId(keys, value)
            if (id !in result) historyValue(value, state)?.let { result[id] = it }
        }
        return result
    }
    private suspend fun splitHistory(url: String, day: Long, start: Long, length: Long, visits: List<Visit>, state: JSONObject, keys: SyncKeys, result: MutableMap<String, JSONObject>, partitioned: Boolean = false) {
        if (visits.size > 2048) {
            requireSync(length > DAY / 8192, "history_record_too_large")
            val half = length / 2
            splitHistory(url, day, start, half, visits.filter { it.timestampUsec < start + half }, state, keys, result, true)
            splitHistory(url, day, start + half, half, visits.filter { it.timestampUsec >= start + half }, state, keys, result, true)
        } else if (visits.isNotEmpty()) {
            val value = obj("version" to 1, "url" to url, "dayStartUsec" to day)
            if (partitioned || length != DAY) value.put("part", obj("startUsec" to start, "lenUsec" to length))
            historyValue(value, state)?.let { result[SyncModels.historyId(keys, value)] = it }
        }
    }
    private suspend fun historyValue(identity: JSONObject, state: JSONObject, raw: Boolean = false): JSONObject? {
        val (start, end) = window(identity); val url = identity.getString("url")
        val dao = history.historyDao()
        val visits = dao.syncVisits(url, start, end)
        if (visits.isEmpty()) return null
        val value = obj("version" to 1, "url" to url, "title" to (dao.getPage(url)?.title ?: ""), "dayStartUsec" to identity.getLong("dayStartUsec"),
            "visits" to JSONArray().apply { visits.forEach { put(obj("atUsec" to it.timestampUsec, "transition" to transition(it.type), "count" to it.count)) } })
        identity.objectOrNull("part")?.let { value.put("part", copy(it)) }
        val removed = identity.objects("removed").toMutableList()
        val removals = dao.syncRemovals()
        identity.objects("visits").forEach { visit ->
            if (removals.any { (it.url == null || it.url == url) && visit.getLong("atUsec") / 1000 in it.startTime..it.endTime }) {
                removed.add(obj("atUsec" to visit.getLong("atUsec"), "transition" to visit.getInt("transition")))
            }
        }
        if (removed.isNotEmpty()) value.put("removed", JSONArray(removed.distinctBy { it.getLong("atUsec") to it.getInt("transition") }
            .sortedWith(compareBy({ it.getLong("atUsec") }, { it.getInt("transition") }))))
        if (raw) return value
        val projection = projections(state).entries().find { (_, entry) -> (entry as? JSONObject)?.getJSONObject("value")?.let {
            it.optString("url") == url && it.optLong("dayStartUsec") == identity.optLong("dayStartUsec") && same(it.opt("part"), identity.opt("part")) } == true }?.first
        return if (projection == null) value else projected(state, projection, value)
    }
    override suspend fun canDelete(name: String, id: String, value: JSONObject, state: JSONObject): Boolean = when (name) {
        "history" -> canDeleteHistory(value)
        "bookmarks" -> bookmarks.bookmarksDao().get(nativeId(state, id)) == null
        "passwords" -> logins.list().none { it.guid == nativeId(state, id) }
        else -> true
    }
    override suspend fun canDeleteHistory(value: JSONObject): Boolean {
        val removals = history.historyDao().syncRemovals()
        return value.objects("visits").all { visit -> removals.any {
            (it.url == null || it.url == value.getString("url")) && visit.getLong("atUsec") / 1000 in it.startTime..it.endTime
        } }
    }
    override suspend fun clearHistoryBefore(ms: Long) = history.withTransaction {
        history.historyDao().deleteVisitsBefore(ms + 1)
        history.historyDao().prunePages()
    }

    private fun loginValue(login: Login) = obj("version" to 1, "origin" to login.origin, "formActionOrigin" to login.formActionOrigin,
        "httpRealm" to login.httpRealm, "username" to login.username, "password" to login.password, "usernameField" to login.usernameField,
        "passwordField" to login.passwordField, "timeCreated" to login.timeCreated, "timePasswordChanged" to login.timePasswordChanged)
    private fun cardValue(card: CreditCard, number: String) = obj("version" to 1, "name" to card.billingName, "number" to number,
        "month" to card.expiryMonth.takeIf { it > 0 }, "year" to card.expiryYear.takeIf { it > 0 })
    private fun addressValue(address: Address) = obj("version" to 1, "nativeVersion" to 1,
        "fields" to obj(*SyncModels.addressFields.zip(listOf(address.name, address.organization, address.streetAddress, address.addressLevel3,
            address.addressLevel2, address.addressLevel1, address.postalCode, address.country, address.tel, address.email)).toTypedArray()),
        "timeCreated" to address.timeCreated, "timeLastModified" to address.timeLastModified, "timeLastUsed" to (address.timeLastUsed ?: 0), "timesUsed" to address.timesUsed)
    private fun nativeBookmarkParent(id: String) = if (id == "mobile______") LOCAL_ROOT else id
    private fun rootTitle(id: String) = when (id) { "menu________" -> "Bookmarks menu"; "toolbar_____" -> "Bookmarks toolbar"; else -> "Other bookmarks" }
    private fun window(identity: JSONObject): Pair<Long, Long> = identity.objectOrNull("part")?.let { it.getLong("startUsec") to it.getLong("startUsec") + it.getLong("lenUsec") }
        ?: (identity.getLong("dayStartUsec") to identity.getLong("dayStartUsec") + DAY)
    private fun transition(type: VisitType): Int = when (type) {
        VisitType.TYPED -> 2; VisitType.BOOKMARK -> 3; VisitType.REDIRECT_PERMANENT -> 5; VisitType.REDIRECT_TEMPORARY -> 6
        VisitType.DOWNLOAD -> 7; VisitType.FRAMED_LINK -> 8; VisitType.RELOAD -> 9; else -> 1
    }
    private fun visitType(type: Int): VisitType = when (type) {
        2 -> VisitType.TYPED; 3 -> VisitType.BOOKMARK; 5 -> VisitType.REDIRECT_PERMANENT; 6 -> VisitType.REDIRECT_TEMPORARY
        7 -> VisitType.DOWNLOAD; 8 -> VisitType.FRAMED_LINK; 9 -> VisitType.RELOAD; else -> VisitType.LINK
    }
    companion object {
        const val DAY = 86_400_000_000L
        const val LOCAL_ROOT = "bookmarks_root"
        val ROOTS = listOf("menu________", "toolbar_____", "unfiled_____")
    }
}
