package org.midorinext.android.newtab

import java.net.URI
import java.util.Locale

internal fun recentHomeSiteKey(url: String): String? = publicHomeUrl(url)?.let {
    val uri = URI(it)
    val host = uri.host.lowercase(Locale.ROOT).removePrefix("www.")
    val defaultPort = if (uri.scheme.equals("https", ignoreCase = true)) 443 else 80
    if (uri.port == -1 || uri.port == defaultPort) host else "$host:${uri.port}"
}
