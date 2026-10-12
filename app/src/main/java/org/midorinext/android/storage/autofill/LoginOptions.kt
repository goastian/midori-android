package org.midorinext.android.storage.autofill

import mozilla.components.concept.storage.Login
import mozilla.components.concept.storage.LoginHint
import java.net.URI
import java.util.Locale

internal fun loginOptions(logins: List<Login>, pageUrl: String): List<Login> = logins
    .filter { it.hint != LoginHint.EMAIL_MASK }
    .sortedWith(
        compareByDescending<Login> { it.hint == LoginHint.GENERATED }
            .thenByDescending { it.hint == LoginHint.MATCHING_ORIGIN }
            .thenByDescending { sameLoginOrigin(it.origin, pageUrl) }
            .thenByDescending { it.timeLastUsed }
            .thenBy { it.origin }
            .thenBy { it.username },
    )
    .distinctBy { listOf(it.username, it.password, it.httpRealm, it.hint == LoginHint.GENERATED) }

internal fun sameLoginOrigin(first: String, second: String): Boolean {
    val firstOrigin = loginOrigin(first) ?: return false
    return firstOrigin == loginOrigin(second)
}

private fun loginOrigin(value: String): Triple<String, String, Int>? = runCatching {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    val host = uri.host?.lowercase(Locale.ROOT) ?: return null
    val port = when {
        uri.port != -1 -> uri.port
        scheme == "https" -> 443
        scheme == "http" -> 80
        else -> return null
    }
    Triple(scheme, host, port)
}.getOrNull()
