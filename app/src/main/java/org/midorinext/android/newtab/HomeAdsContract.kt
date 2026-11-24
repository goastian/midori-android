package org.midorinext.android.newtab

import org.json.JSONObject
import java.net.URI
import java.util.Locale

internal const val HOME_ADS_ORIGIN = "https://ads.astian.org"
internal val homeAdsKeyPattern = Regex("[a-z0-9][a-z0-9_-]{0,79}")

internal fun homeAdsOrigin(value: String, debug: Boolean): String {
    val uri = URI(value)
    val local = debug && uri.scheme == "http" && uri.host in setOf("localhost", "127.0.0.1", "[::1]")
    require(value == HOME_ADS_ORIGIN || local)
    require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null && uri.path.isNullOrEmpty())
    return value
}

data class HomeAdsBookmark(
    val key: String,
    val title: String,
    val url: String,
    val category: String,
    val iconUrl: String,
    val default: Boolean,
    val suggested: Boolean,
) {
    val isHomeDefault: Boolean get() = default && category == "general"
}

data class HomeAdContent(val title: String, val iconUrl: String, val sponsorLabel: String?)

data class HomeAdDecision(
    val bookmarkKey: String,
    val decisionId: String,
    val title: String,
    val iconUrl: String,
    val destinationUrl: String,
    val impressionToken: String,
    val expiresAt: Long,
    val sponsorLabel: String?,
    val dataUsed: List<String>,
) {
    fun isActive(now: Long = System.currentTimeMillis()) = expiresAt > now
    fun content() = HomeAdContent(title, iconUrl, sponsorLabel)
}

private fun adsUrl(value: String, origin: String, pathPrefix: String? = null): String? = runCatching {
    val uri = URI(value)
    val base = URI(origin)
    value.takeIf {
        uri.scheme == base.scheme && uri.host == base.host &&
            (if (uri.port == -1) if (uri.scheme == "https") 443 else 80 else uri.port) ==
            (if (base.port == -1) if (base.scheme == "https") 443 else 80 else base.port) &&
            uri.rawUserInfo == null && uri.rawFragment == null &&
            (pathPrefix == null || uri.rawPath.startsWith(pathPrefix))
    }
}.getOrNull()

internal fun parseHomeAdsCatalog(json: JSONObject, origin: String = HOME_ADS_ORIGIN): List<HomeAdsBookmark> {
    require(json.getString("contract_version") == "ads-bookmarks-v1")
    val entries = json.getJSONArray("bookmarks")
    require(entries.length() <= 128)
    val keys = mutableSetOf<String>()
    return (0 until entries.length()).map { index ->
        val entry = entries.getJSONObject(index)
        val key = entry.getString("key")
        require(homeAdsKeyPattern.matches(key) && keys.add(key))
        val title = entry.getString("title").trim()
        val url = publicHomeUrl(entry.getString("url"))
        val category = entry.getString("category")
        require(title.isNotEmpty() && title.length <= 120 && url != null && url.length <= 2048)
        require(homeAdsKeyPattern.matches(category))
        val icon = adsUrl(entry.getString("icon_url"), origin)
        require(icon != null && icon.length <= 2048)
        HomeAdsBookmark(key, title, url, category, icon, entry.getBoolean("default"), entry.getBoolean("suggested"))
    }
}

internal fun parseHomeAdDecision(json: JSONObject, key: String, now: Long = System.currentTimeMillis(), origin: String = HOME_ADS_ORIGIN): HomeAdDecision {
    require(json.getString("contract_version") == "ads-channel-v1")
    require(json.getString("format_id") == "newtab_icon_v1")
    require(json.getString("channel") == "midori_tab" && json.getString("placement") == "new_tab")
    require(json.getString("bookmark_key") == key && homeAdsKeyPattern.matches(key))
    val billing = json.getJSONObject("billing")
    val funding = billing.getString("funding_type")
    require(billing.getString("model") == "vcpm" && funding in setOf("house", "prepaid"))
    val amount = billing.getLong("amount_micros")
    require(amount >= 0 && (funding != "house" || amount == 0L))
    require(Regex("[A-Z]{3}").matches(billing.getString("currency")))
    val disclosed = json.getBoolean("disclosure_required")
    require(disclosed == (funding == "prepaid"))
    val label = if (disclosed) json.getString("sponsor_label").also { require(it.isNotBlank() && it.length <= 80) } else null
    val dataUsed = if (disclosed) {
        val transparency = json.getJSONObject("transparency")
        require(transparency.getInt("frequency_cap_per_day") >= 0 && transparency.getBoolean("feedback_enabled"))
        val fields = transparency.getJSONArray("data_used")
        require(fields.length() in 1..10)
        (0 until fields.length()).map { fields.getString(it) }.also {
            require(it.all { field -> field in setOf("language", "device_type", "country", "categories", "subregion") })
        }
    } else {
        require(json.isNull("sponsor_label") && json.isNull("transparency"))
        emptyList()
    }
    listOf("opportunity_id", "request_id", "decision_id", "impression_token", "attribution_token").forEach {
        require(json.getString(it).let { value -> value.isNotBlank() && value.length <= 16384 })
    }
    require(json.getLong("ad_id") > 0)
    val expiry = Math.multiplyExact(json.getLong("expires_at"), 1000)
    require(expiry > now + 30_000 && expiry <= now + 86_400_000)
    val icon = adsUrl(json.optString("icon_url").ifBlank { json.getString("image_url") }, origin)
    val destination = adsUrl(json.getString("destination_url"), origin, "/api/v1/ads/click/")
    val title = json.getString("title").trim()
    require(icon != null && destination != null && title.isNotEmpty() && title.length <= 120)
    return HomeAdDecision(key, json.getString("decision_id"), title, icon, destination,
        json.getString("impression_token"), expiry, label, dataUsed)
}

internal fun homeAdsLanguage(locale: Locale): String = if (locale.language == "es") "es" else "en"
