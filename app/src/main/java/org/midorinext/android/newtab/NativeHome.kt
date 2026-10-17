package org.midorinext.android.newtab

import java.net.URI
import org.midorinext.android.preferences.app.HomeShortcut

// A real document keeps Home in Gecko's back stack without rendering a web surface.
const val NATIVE_HOME_URL = "data:text/html;charset=utf-8,%3Ctitle%3EMidori%3C/title%3E%3Cbody%3E%3C/body%3E#midori-native-home"
const val LEGACY_NEW_TAB_URL = "about:blank#midori-newtab"
const val LEGACY_NATIVE_HOME_URL = "data:text/html;base64,PCFkb2N0eXBlIGh0bWw+PHRpdGxlPk1pZG9yaTwvdGl0bGU+PCEtLW1pZG9yaS1uYXRpdmUtaG9tZS0tPg=="

fun isNativeHomeUrl(url: String?): Boolean = url == NATIVE_HOME_URL || url == LEGACY_NEW_TAB_URL || url == LEGACY_NATIVE_HOME_URL

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
    Triple("p2pme", "P2P.me", "https://app.p2p.me/recommend?address=0xB3082b37cC95a9d4e6B1aEC19719a00c37D47597&nonce=215422&signature=0x161f733dab2a16e55bc8b2000a2a22be178c884d932916cf62a72c96154c059c163b749bd522672fbb7dccefa56f9127c14c7d747335ddbb10d65cc2ed33344f1b"),
    Triple("kayak-cars", "Kayak Cars", "https://www.kayak.com/in?a=kan_318921_594075&lc=en&url=%2Fcars"),
    Triple("temu", "Temu", "https://temu.to/k/gxr990d7ytg"),
).map { (id, title, url) ->
    HomeShortcut.newBuilder().setId(id).setTitle(title).setUrl(url).build()
}

enum class HomeSection { SHORTCUTS, CONTINUE, RECENT_VISITS, STORIES, PRIVACY }
