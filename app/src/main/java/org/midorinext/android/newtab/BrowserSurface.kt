package org.midorinext.android.newtab

enum class BrowserSurface { HOME, PRIVATE_HOME, BLANK, WEB }

fun browserSurface(
    url: String,
    private: Boolean,
    isHome: Boolean = isNativeHomeUrl(url),
): BrowserSurface = when {
    private && (url.isEmpty() || isHome) -> BrowserSurface.PRIVATE_HOME
    isHome -> BrowserSurface.HOME
    url == "about:blank" -> BrowserSurface.BLANK
    else -> BrowserSurface.WEB
}
