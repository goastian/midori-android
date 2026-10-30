package org.midorinext.android.sync

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    internal val bookmarks: BookmarksDatabase,
    internal val history: HistoryDatabase,
    private val logins: LoginAutofillRepository,
    private val autofill: dagger.Lazy<AutofillCreditCardsAddressesStorage>,
    private val forms: SyncFormHistory,
) : SyncAdapter {
    internal fun links(state: JSONObject) = state.objectOrNull("links") ?: JSONObject().also { state.put("links", it) }
    private fun projections(state: JSONObject) = state.objectOrNull("projections") ?: JSONObject().also { state.put("projections", it) }
    internal fun nativeId(state: JSONObject, id: String) = links(state).optString(id, id)
    internal fun wireId(state: JSONObject, id: String): String {
        val candidate = portableGuid(id)
        if (!links(state).has(candidate) || links(state).optString(candidate) == id) return candidate
        return links(state).entries().firstOrNull { it.second == id }?.first ?: candidate
    }
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
    internal fun projected(state: JSONObject, id: String, raw: JSONObject): JSONObject {
        val previous = projections(state).objectOrNull(id) ?: return raw
        val result = copy(previous.getJSONObject("value"))
        val native = previous.getJSONObject("native")
        for ((key, value) in raw.entries()) if (!same(value, native.opt(key))) result.put(key, value)
        return result
    }
    internal fun shadow(state: JSONObject, id: String, raw: JSONObject, value: JSONObject) {
        projections(state).put(id, obj("native" to raw, "value" to value))
    }
    private val journalLock = Mutex()
    @Volatile private var journalReady = false
    internal suspend fun prepareJournals() {
        if (journalReady) return
        journalLock.withLock {
            if (!journalReady) withContext(Dispatchers.IO) {
                bookmarks.withTransaction { SyncJournalSchema.create(bookmarks.openHelper.writableDatabase, history = false) }
                history.withTransaction { SyncJournalSchema.create(history.openHelper.writableDatabase, history = true) }
                journalReady = true
            }
        }
    }
    fun localChanges(): Flow<Pair<Long, Long>> = combine(
        bookmarks.syncJournalDao().localRevisions(), history.syncJournalDao().localRevisions()
    ) { a, b -> a to b }.distinctUntilChanged().flowOn(Dispatchers.IO)

    suspend fun hasPendingLocal(collections: JSONObject, selected: Set<String>): Boolean {
        prepareJournals()
        for ((name, journal) in listOf("bookmarks" to bookmarks.syncJournalDao(), "history" to history.syncJournalDao())) {
            if (name !in selected) continue
            val revision = journal.revision()
            val collection = collections.optJSONObject(name)
            if (collection == null || collection.optString("native_epoch") != revision.epoch || collection.optLong("native_local_revision", -1) != revision.localRevision) return true
        }
        return false
    }
    override suspend fun capture(name: String, state: JSONObject, keys: SyncKeys, persist: () -> Unit,
                                 consume: suspend (Map<String, JSONObject?>) -> Unit) {
        if (name == "bookmarks" || name == "history") captureNative(name, state, keys, persist, consume)
        else super<SyncAdapter>.capture(name, state, keys, persist, consume)
    }
    override suspend fun snapshot(name: String, state: JSONObject, keys: SyncKeys): Map<String, JSONObject> = when (name) {
        "bookmarks", "history" -> nativeSnapshot(name, state, keys)
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
        if (name == "bookmarks" || name == "history") nativeScope(name, state, keys)
        if (name == "history" && hint != null) return historyValue(hint, state, id = id)
        if (name == "bookmarks") return bookmarks.withTransaction {
            bookmarks.bookmarksDao().get(indexedNativeId(name, id, state))?.let { bookmarkValue(it, state) }
        }
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
            "bookmarks" -> bookmarks.remoteSyncWrite(bookmarks.syncJournalDao()) {
                requireSync(same(read(name, id, hint ?: value ?: expected, state, keys), expected), "local_changed")
                val dao = bookmarks.bookmarksDao()
                val localId = indexedNativeId(name, id, state)
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
                    shadow(state, id, bookmarkValue(dao.get(localId)!!, state, raw = true)!!, value)
                }
            }
            "history" -> history.remoteSyncWrite(history.syncJournalDao()) {
                val identity = value ?: hint ?: expected ?: return@remoteSyncWrite
                requireSync(same(historyValue(identity, state, id = id), expected), "local_changed")
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
                    history.syncJournalDao().remember(SyncRecordIndex(state.getString("native_scope"), id, url, identity.getLong("dayStartUsec"), start, end))
                    shadow(state, id, historyValue(value, state, raw = true, id = id) ?: copy(value), value)
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
    internal suspend fun historyValue(identity: JSONObject, state: JSONObject, raw: Boolean = false, id: String? = null, pageTitle: String? = null): JSONObject? = history.withTransaction {
        val (start, end) = window(identity)
        val url = identity.getString("url")
        val dao = history.historyDao()
        val visits = dao.syncWindowPage(url, start, end, 2049)
        if (visits.isEmpty()) return@withTransaction null
        requireSync(visits.size <= 2048, "history_record_too_large")
        val value = obj("version" to 1, "url" to url, "title" to (pageTitle ?: dao.getPage(url)?.title.orEmpty()), "dayStartUsec" to identity.getLong("dayStartUsec"),
            "visits" to JSONArray().apply { visits.forEach { put(obj("atUsec" to it.timestampUsec, "transition" to transition(it.type), "count" to it.count)) } })
        identity.objectOrNull("part")?.let { value.put("part", copy(it)) }
        // Incoming tombstones belong to the remote side of the three-way merge.
        // Copying them into a local read can produce both a visit and its tombstone,
        // making a valid offline edit look like an invalid local record.
        val removalBasis = if (raw) identity else id?.let {
            state.optJSONObject("mirror")?.optJSONObject(it)?.objectOrNull("value")
        }
        val removed = removalBasis?.objects("removed").orEmpty().toMutableList()
        if (identity.has("visits")) {
            val removals = dao.syncRemovalsFor(url, start / 1000, (end - 1) / 1000)
            identity.objects("visits").forEach { visit ->
                if (removals.any { visit.getLong("atUsec") / 1000 in it.startTime..it.endTime }) {
                    removed.add(obj("atUsec" to visit.getLong("atUsec"), "transition" to visit.getInt("transition")))
                }
            }
        }
        if (removed.isNotEmpty()) value.put("removed", JSONArray(removed.distinctBy { it.getLong("atUsec") to it.getInt("transition") }
            .sortedWith(compareBy({ it.getLong("atUsec") }, { it.getInt("transition") }))))
        if (raw) return@withTransaction value
        val projection = id ?: history.syncJournalDao().windows(state.getString("native_scope"), url, identity.getLong("dayStartUsec"))
            .firstOrNull { it.windowStart == start && it.windowEnd == end }?.id
        if (projection == null) value else projected(state, projection, value)
    }
    override suspend fun markPending(name: String, id: String, hint: JSONObject?, state: JSONObject) {
        when (name) {
            "bookmarks" -> bookmarks.syncJournalDao().markLocal("node", indexedNativeId(name, id, state))
            "history" -> if (hint != null) history.syncJournalDao().markLocal("day", hint.getString("url") + '\u001f' + hint.getLong("dayStartUsec"))
        }
    }
    override suspend fun canDelete(name: String, id: String, value: JSONObject, state: JSONObject): Boolean = when (name) {
        "history" -> canDeleteHistory(value)
        "bookmarks" -> bookmarks.bookmarksDao().get(indexedNativeId(name, id, state)) == null
        "passwords" -> logins.list().none { it.guid == nativeId(state, id) }
        else -> true
    }
    override suspend fun canDeleteHistory(value: JSONObject): Boolean {
        val (start, end) = window(value)
        val removals = history.historyDao().syncRemovalsFor(value.getString("url"), start / 1000, (end - 1) / 1000)
        return value.objects("visits").all { visit -> removals.any {
            (it.url == null || it.url == value.getString("url")) && visit.getLong("atUsec") / 1000 in it.startTime..it.endTime
        } }
    }
    override suspend fun clearHistoryBefore(ms: Long) = history.remoteSyncWrite(history.syncJournalDao()) {
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
    internal fun window(identity: JSONObject): Pair<Long, Long> = identity.objectOrNull("part")?.let { it.getLong("startUsec") to it.getLong("startUsec") + it.getLong("lenUsec") }
        ?: (identity.getLong("dayStartUsec") to identity.getLong("dayStartUsec") + DAY)
    internal fun transition(type: VisitType): Int = when (type) {
        VisitType.TYPED -> 2; VisitType.BOOKMARK -> 3; VisitType.REDIRECT_PERMANENT -> 5; VisitType.REDIRECT_TEMPORARY -> 6
        VisitType.DOWNLOAD -> 7; VisitType.FRAMED_LINK -> 8; VisitType.RELOAD -> 9; else -> 1
    }
    internal fun visitType(type: Int): VisitType = when (type) {
        2 -> VisitType.TYPED; 3 -> VisitType.BOOKMARK; 5 -> VisitType.REDIRECT_PERMANENT; 6 -> VisitType.REDIRECT_TEMPORARY
        7 -> VisitType.DOWNLOAD; 8 -> VisitType.FRAMED_LINK; 9 -> VisitType.RELOAD; else -> VisitType.LINK
    }
    companion object {
        const val DAY = 86_400_000_000L
        const val LOCAL_ROOT = "bookmarks_root"
        val ROOTS = listOf("menu________", "toolbar_____", "unfiled_____")
    }
}
