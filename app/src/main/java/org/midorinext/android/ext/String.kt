package org.midorinext.android.ext

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.midorinext.android.BuildConfig
import java.net.URI
import java.net.URLDecoder

fun String.isMidoriUrl(): Boolean {
    // TODO use regex
    return (this.startsWith(BuildConfig.QWANT_BASE_URL)
            && !this.startsWith("${BuildConfig.QWANT_BASE_URL}/maps")
            && !this.startsWith("${BuildConfig.QWANT_BASE_URL}/flight"))
}

fun String.isMidoriUrlValid(): Boolean {
    return this.indexOf("&qbc=1") != -1
}

fun String.isMidoriSERPUrl(): Boolean {
    return this.isMidoriUrl()
            && (this.contains("&q=") || this.contains("?q="))
}

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
        URI(this).normalize().host.removePrefix("www.")
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