package org.midorinext.android.sync

import java.net.URI
import org.midorinext.android.BuildConfig

internal data class SyncPairing(val server: String, val token: String) {
    companion object {
        fun parse(contents: String): SyncPairing {
            requireSync(contents.length <= 512, "invalid_pairing_qr")
            val uri = runCatching { URI(contents) }.getOrNull()
                ?: throw SyncException("invalid_pairing_qr")
            requireSync(uri.rawPath == "/pair" && uri.rawQuery == null && uri.rawUserInfo == null,
                "invalid_pairing_qr")
            val server = runCatching { SyncTransport.normalize(URI(uri.scheme, null, uri.host, uri.port, "/", null, null).toString()) }
                .getOrElse { throw SyncException("invalid_pairing_qr") }
            val local = BuildConfig.DEBUG && uri.scheme == "http" && uri.host in listOf("localhost", "127.0.0.1", "[::1]")
            requireSync(server == SyncTransport.DEFAULT_SERVER || local, "invalid_pairing_qr")
            val token = Regex("token=([A-F0-9]{16})").matchEntire(uri.rawFragment.orEmpty())?.groupValues?.get(1)
                ?: throw SyncException("invalid_pairing_qr")
            return SyncPairing(server, token)
        }
    }
}
