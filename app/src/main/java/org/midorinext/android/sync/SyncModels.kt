package org.midorinext.android.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

internal object SyncModels {
    val schemas = mapOf("bookmarks" to 2, "history" to 2, "passwords" to 1, "credit-cards" to 1, "addresses" to 1, "form-history" to 1)
    val features = mapOf("bookmarks" to listOf("bookmark_labels"), "history" to listOf("history_parts", "history_visit_removals"),
        "credit-cards" to listOf("credit_cards"), "addresses" to listOf("addresses"), "form-history" to listOf("form_history"))
    val addressFields = listOf("name", "organization", "street-address", "address-level3", "address-level2", "address-level1", "postal-code", "country", "tel", "email")
    private val metadata = listOf("timeCreated", "timeLastModified", "timeLastUsed", "timesUsed")
    fun http(value: String): Boolean = runCatching { URI(value).let { it.scheme in listOf("https", "http") && !it.host.isNullOrBlank() && it.userInfo == null && value.length <= 65536 } }.getOrDefault(false)
    fun supported(capabilities: JSONObject, name: String): Boolean {
        val collections = capabilities.optJSONArray("collections") ?: return false
        val features = capabilities.optJSONArray("features") ?: return false
        return (0 until collections.length()).any { collections.optString(it) == name } &&
            capabilities.optJSONObject("data_schemas")?.opt(name) == schemas[name] &&
            this.features[name].orEmpty().all { feature -> (0 until features.length()).any { features.optString(it) == feature } }
    }
    fun validate(keys: SyncKeys, name: String, id: String, value: JSONObject): JSONObject {
        requireSync(value.optInt("version") == 1)
        if (name in listOf("bookmarks", "history", "passwords")) {
            if (name == "passwords") requireSync(id.length in 1..64 && id.all { it.code in 0x20..0x7e && it != ',' })
            keys.model(name, obj("action" to "validate", "value" to value, *(if (name == "bookmarks") arrayOf("id" to id) else emptyArray())))
            if (name == "history") requireSync(id == historyId(keys, value))
            return value
        }
        when (name) {
            "credit-cards" -> {
                requireSync(id.matches(Regex("[A-Za-z0-9_-]{12}")))
                requireSync(value.keys().asSequence().toSet() == setOf("version", "name", "number", "month", "year") &&
                    value.getString("name").length <= 512 && value.getString("number").matches(Regex("[0-9]{12,19}")) &&
                    (value.isNull("month") || value.getInt("month") in 1..12) && (value.isNull("year") || value.getInt("year") in 1900..9999))
            }
            "addresses" -> {
                requireSync(id.matches(Regex("[A-Za-z0-9_-]{12}")))
                requireSync(value.keys().asSequence().toSet() == (setOf("version", "nativeVersion", "fields") + metadata) && value.getInt("nativeVersion") == 1)
                val fields = value.getJSONObject("fields")
                requireSync(fields.keys().asSequence().all { it in addressFields && fields.get(it) is String && fields.getString(it).length <= 8192 && !fields.getString(it).contains('\u0000') })
                metadata.forEach { requireSync(value.getLong(it) in 0..9007199254740991L) }
                requireSync(value.toString().toByteArray().size <= 131072)
            }
            "form-history" -> {
                requireSync(value.keys().asSequence().toSet() == setOf("version", "fieldname", "value", "firstUsed", "lastUsed", "timesUsed") &&
                    value.getString("fieldname").length in 1..200 && value.getString("value").length in 1..200 &&
                    value.getLong("firstUsed") > 0 && value.getLong("lastUsed") >= value.getLong("firstUsed") && value.getLong("timesUsed") > 0)
                val field = value.getString("fieldname").lowercase().replace(Regex("[^a-z0-9]"), "")
                requireSync(field !in setOf("password", "passwd", "pwd", "passphrase", "currentpassword", "newpassword", "otp", "onetimecode", "ccnumber", "cccsc", "cccvv", "cvv", "cvc", "accesstoken", "refreshtoken", "csrftoken"))
                requireSync(id == "f1:" + keys.derive(name, JSONArray().put(value.getString("fieldname")).put(value.getString("value"))))
            }
        }
        return value
    }
    fun historyId(keys: SyncKeys, value: JSONObject): String {
        val input = JSONArray().put(value.getString("url")).put(value.getLong("dayStartUsec"))
        value.objectOrNull("part")?.let { input.put(it.getLong("startUsec")).put(it.getLong("lenUsec")) }
        return "h2:" + keys.derive("history", input)
    }
    fun merge(keys: SyncKeys, name: String, id: String, base: JSONObject?, local: JSONObject?, remote: JSONObject?): JSONObject {
        if (name in listOf("bookmarks", "history", "passwords")) return keys.model(name,
            obj("action" to "merge", "base" to base, "local" to local, "remote" to remote,
                *(if (name == "bookmarks") arrayOf("id" to id) else emptyArray())))
        if (same(local, remote) || same(base, remote)) return obj("status" to "keep_local")
        if (same(base, local)) return obj("status" to "apply", "value" to remote)
        if (local == null || remote == null) return obj("status" to "conflict", "reason" to "deletion_edit")
        if (name == "form-history") return obj("status" to "apply", "value" to copy(local)
            .put("firstUsed", minOf(local.getLong("firstUsed"), remote.getLong("firstUsed")))
            .put("lastUsed", maxOf(local.getLong("lastUsed"), remote.getLong("lastUsed")))
            .put("timesUsed", maxOf(local.getLong("timesUsed"), remote.getLong("timesUsed"))))
        val merged = copy(local)
        val left = if (name == "addresses") local.getJSONObject("fields") else local
        val right = if (name == "addresses") remote.getJSONObject("fields") else remote
        val before = if (name == "addresses") base?.getJSONObject("fields") else base
        val selected = if (name == "addresses") copy(left) else merged
        for (field in if (name == "addresses") addressFields else listOf("name", "number", "month", "year")) {
            val l = left.opt(field); val r = right.opt(field); val b = before?.opt(field)
            if (same(l, r) || same(b, r)) continue
            if (!same(b, l)) return obj("status" to "conflict", "reason" to "concurrent_fields")
            if (right.has(field)) selected.put(field, r) else selected.remove(field)
        }
        if (name == "addresses") {
            merged.put("fields", selected)
            metadata.forEach { field -> merged.put(field, if (field == "timeCreated") minOf(local.getLong(field), remote.getLong(field)) else maxOf(local.getLong(field), remote.getLong(field))) }
        }
        return obj("status" to "apply", "value" to merged)
    }
}
