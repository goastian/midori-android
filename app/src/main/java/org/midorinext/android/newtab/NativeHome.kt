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
    Triple("google", "Google", "https://www.google.com/"),
    Triple("wikipedia", "Wikipedia", "https://www.wikipedia.org/"),
    Triple("midori", "Midori", "https://astian.org/midori-browser/"),
).map { (id, title, url) ->
    HomeShortcut.newBuilder().setId(id).setTitle(title).setUrl(url).build()
}

enum class HomeSection { SHORTCUTS, CONTINUE, RECENT_VISITS, STORIES, PRIVACY }
