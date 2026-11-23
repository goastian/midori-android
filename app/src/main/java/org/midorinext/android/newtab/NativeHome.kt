package org.midorinext.android.newtab

import java.net.URI
import org.midorinext.android.newtab.migration.isLegacyNativeHomeUrl
import org.midorinext.android.preferences.app.HomeShortcut

// A real document keeps Home in Gecko's back stack without rendering a web surface.
const val NATIVE_HOME_URL = "data:text/html;charset=utf-8,%3Ctitle%3EMidori%3C/title%3E%3Cbody%3E%3C/body%3E#midori-native-home"
fun isNativeHomeUrl(url: String?): Boolean = url == NATIVE_HOME_URL || isLegacyNativeHomeUrl(url)

fun publicHomeUrl(value: String): String? = runCatching {
    val trimmed = value.trim()
    val url = if ("://" in trimmed) trimmed else "https://$trimmed"
    val uri = URI(url)
    url.takeIf {
        uri.scheme.lowercase() in setOf("https", "http") &&
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }
}.getOrNull()

fun defaultHomeShortcuts(): List<HomeShortcut> = listOf(
    Triple("kayak", "Kayak", "https://www.kayak.com/in?a=kan_318921_594075&lc=en&url=%2Fflights/"),
    Triple("amazon", "Amazon", "https://amzn.to/47IiNIO"),
    Triple("aliexpress", "AliExpress", "https://s.click.aliexpress.com/e/_Dlf1B1R/"),
    Triple("ebay", "Ebay", "https://ebay.us/gW9r3z/"),
    Triple("kayak-hotels", "Kayak Hotels", "https://www.kayak.com/in?a=kan_318921_594075&lc=en&url=%2Fhotels"),
    Triple("midori-wallet", "Midori Wallet", "https://midoriwallet.com/wallet"),
    Triple("kayak-cars", "Kayak Cars", "https://www.kayak.com/in?a=kan_318921_594075&lc=en&url=%2Fcars"),
    Triple("temu", "Temu", "https://temu.to/k/gxr990d7ytg"),
).map { (id, title, url) ->
    HomeShortcut.newBuilder().setId(id).setTitle(title).setUrl(url).setAdsBookmarkKey(id).setAdsDefault(true).build()
}

enum class HomeSection { SHORTCUTS, CONTINUE, RECENT_VISITS, STORIES, PRIVACY }
