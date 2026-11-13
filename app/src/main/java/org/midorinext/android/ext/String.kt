package org.midorinext.android.ext

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import java.net.URI
import java.net.URLDecoder

private val retiredSearchHosts = setOf("astiango.com", "www.astiango.com", "astiango.org", "www.astiango.org")

fun String.isRetiredSearchUrl(): Boolean = runCatching {
    val uri = URI(this)
    uri.scheme in setOf("https", "http") && uri.host?.lowercase() in retiredSearchHosts &&
        uri.path.orEmpty() in setOf("", "/", "/search")
}.getOrDefault(false)

fun String.isMidoriUrl(): Boolean = isRetiredSearchUrl() || runCatching {
    val uri = URI(this)
    uri.scheme == "https" && uri.host?.lowercase() in setOf("www.google.com", "google.com") &&
        uri.path.orEmpty() in setOf("", "/", "/search")
}.getOrDefault(false)

fun String.isMidoriSERPUrl(): Boolean {
    return this.isMidoriUrl()
            && (this.contains("&q=") || this.contains("?q="))
}

/** Matches only the URL shape used by the retired remote New Tab implementation. */
fun String.isLegacyMidoriHomeUrl(): Boolean = runCatching {
    val candidate = URI(this).normalize()
    val path = candidate.path.orEmpty()
    val parameters = candidate.rawQuery
        .orEmpty()
        .split('&')
        .filter(String::isNotBlank)
        .map { parameter -> parameter.substringBefore('=') to parameter.substringAfter('=', "") }

    candidate.scheme.equals("https", ignoreCase = true) &&
        candidate.host?.lowercase() in retiredSearchHosts &&
        candidate.port in setOf(-1, 443) &&
        (path.isEmpty() || path == "/") &&
        parameters.contains("omnibar" to "1") &&
        parameters.contains("qbc" to "1") &&
        parameters.none { (key, _) -> key == "q" }
}.getOrDefault(false)

fun String.getMidoriSERPSearch(): String? {
    if (this.isMidoriSERPUrl()) {
        return this.split("?q=", "&q=")[1].split("&")[0]
    }
    return null
}

fun String.getMidoriSERPCategory(): String? {
    if (this.isMidoriSERPUrl()) {
        val firstSplit = this.split("?t=", "&t=")
        if (firstSplit.size > 1) {
            return firstSplit[1].split("&")[0]
        }
    }
    return null
}

fun String.urlDecode(): String {
    return URLDecoder.decode(this, Charsets.UTF_8.name())
}

fun String.toCleanUrl(): String {
    return this
        .removePrefix("http://")
        .removePrefix("https://")
        // TODO use 'hostWithoutCommonPrefixes' from mozilla support ktx
        //  https://github.com/mozilla-mobile/firefox-android/blob/main/android-components/components/support/ktx/src/main/java/mozilla/components/support/ktx/android/net/Uri.kt
        .removePrefix("www.")
        // .substringBefore('?')
}

fun String.toCleanHost(): String {
    // TODO use 'toShortUrl' from mozilla support ktx there
    //  https://github.com/mozilla-mobile/firefox-android/blob/main/android-components/components/support/ktx/src/main/java/mozilla/components/support/ktx/kotlin/String.kt
    return try {
        val uri = URI(this).normalize()
        if (uri.scheme == "moz-extension") "" else uri.host.orEmpty().removePrefix("www.")
    } catch (e: Exception) {
        Log.w("QB", "Could not normalize url and get the host. Fallback to empty string for security concerns")
        ""
    }
}

fun String.isValidUrl(): Boolean {
    return if (this.startsWith("http://") || this.startsWith("https://")) {
        try {
            URI(this).normalize()
            true
        } catch (e: Exception) {
            false
        }
    } else false
}

@Composable
fun String.toCleanUrlAnnotatedString(color: Color): AnnotatedString {
    val cleaned = this.toCleanUrl()
    val startIndex = cleaned.indexOf('?')

    return if (startIndex != -1) {
        return AnnotatedString.Builder().apply {
            append(cleaned)
            addStyle(SpanStyle(color), startIndex, cleaned.length)
        }.toAnnotatedString()
    } else AnnotatedString(cleaned)
}
