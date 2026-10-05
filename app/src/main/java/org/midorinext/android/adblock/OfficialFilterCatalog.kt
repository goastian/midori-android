package org.midorinext.android.adblock

import java.util.Locale

/** Publisher-maintained subscriptions. The APK contains only the EasyList/EasyPrivacy fallback. */
data class OfficialFilter(
    val id: String,
    val title: String,
    val url: String,
    val tracker: Boolean,
    val defaultEnabled: Boolean = false,
    val localeDefaults: Set<String> = emptySet(),
    val fallbackAsset: String? = null,
) {
    val regional: Boolean get() = localeDefaults.isNotEmpty()
}

object OfficialFilterCatalog {
    val all = listOf(
        OfficialFilter("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true,
            defaultEnabled = true, fallbackAsset = "trackers"),
        OfficialFilter("ubo-privacy", "uBlock Origin · Privacy",
            "https://ublockorigin.github.io/uAssetsCDN/filters/privacy.min.txt", true, defaultEnabled = true),
        OfficialFilter("adguard-tracking", "AdGuard · Tracking Protection",
            "https://filters.adtidy.org/extension/chromium/filters/3.txt", true, defaultEnabled = true),
        OfficialFilter("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", false,
            defaultEnabled = true, fallbackAsset = "ads"),
        OfficialFilter("ubo-ads", "uBlock Origin · Ads",
            "https://ublockorigin.github.io/uAssetsCDN/filters/filters.min.txt", false, defaultEnabled = true),
        OfficialFilter("ubo-unbreak", "uBlock Origin · Unbreak",
            "https://ublockorigin.github.io/uAssetsCDN/filters/unbreak.min.txt", false, defaultEnabled = true),
        OfficialFilter("ubo-quick-fixes", "uBlock Origin · Quick fixes",
            "https://ublockorigin.github.io/uAssetsCDN/filters/quick-fixes.txt", false),
        OfficialFilter("adguard-base", "AdGuard · Base",
            "https://filters.adtidy.org/extension/chromium/filters/2.txt", false, defaultEnabled = true),
        OfficialFilter("adguard-mobile", "AdGuard · Mobile Ads",
            "https://filters.adtidy.org/extension/chromium/filters/11.txt", false, defaultEnabled = true),
        OfficialFilter("regional-albanian", "Albanian · Adblock List for Albania",
            "https://raw.githubusercontent.com/AnXh3L0/blocklist/master/albanian-easylist-addition/Albania.txt",
            false, localeDefaults = setOf("sq")),
        OfficialFilter("regional-chinese", "Chinese · AdGuard",
            "https://filters.adtidy.org/extension/chromium/filters/224.txt",
            false, localeDefaults = setOf("zh")),
        OfficialFilter("regional-german", "German · EasyList Germany",
            "https://easylist.to/easylistgermany/easylistgermany.txt",
            false, localeDefaults = setOf("de")),
        OfficialFilter("regional-french", "French · AdGuard",
            "https://filters.adtidy.org/extension/chromium/filters/16.txt",
            false, localeDefaults = setOf("fr")),
        OfficialFilter("regional-indian", "Indian languages · IndianList",
            "https://easylist-downloads.adblockplus.org/indianlist.txt",
            false, localeDefaults = setOf("hi")),
        OfficialFilter("regional-italian", "Italian · EasyList Italy",
            "https://easylist-downloads.adblockplus.org/easylistitaly.txt",
            false, localeDefaults = setOf("it")),
        OfficialFilter("regional-japanese", "Japanese · AdGuard",
            "https://filters.adtidy.org/extension/chromium/filters/7.txt",
            false, localeDefaults = setOf("ja")),
        OfficialFilter("regional-russian", "Russian · AdGuard",
            "https://filters.adtidy.org/extension/chromium/filters/1.txt",
            false, localeDefaults = setOf("ru")),
        OfficialFilter("regional-spanish", "Spanish · EasyList Spanish",
            "https://easylist-downloads.adblockplus.org/easylistspanish.txt",
            false, localeDefaults = setOf("es")),
        OfficialFilter("regional-spanish-portuguese", "Spanish/Portuguese · AdGuard",
            "https://filters.adtidy.org/extension/chromium/filters/9.txt",
            false, localeDefaults = setOf("es", "pt")),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): OfficialFilter? = byId[id]

    fun defaults(language: String): Set<String> = all.filter { filter ->
        filter.defaultEnabled || language.lowercase(Locale.ROOT) in filter.localeDefaults
    }.mapTo(mutableSetOf()) { it.id }
}
