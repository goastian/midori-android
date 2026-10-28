package org.midorinext.android.sync

import org.junit.Assert.*
import org.junit.Test

class SyncPairingTest {
    @Test fun portal_qr_uses_the_official_server_without_manual_configuration() {
        val pairing = SyncPairing.parse("https://sync.astian.org/pair#token=0123456789ABCDEF")
        assertEquals(SyncTransport.DEFAULT_SERVER, pairing.server)
        assertEquals("0123456789ABCDEF", pairing.token)
        assertEquals(pairing, SyncPairing.parse("https://sync.astian.org:443/pair#token=0123456789ABCDEF"))
    }

    @Test fun qr_cannot_redirect_account_credentials_to_another_server() {
        for (url in listOf(
            "https://other.example/pair#token=0123456789ABCDEF",
            "https://sync.astian.org.attacker.example/pair#token=0123456789ABCDEF",
            "https://sync.astian.org:8443/pair#token=0123456789ABCDEF",
            "https://user@sync.astian.org/pair#token=0123456789ABCDEF",
            "http://sync.astian.org/pair#token=0123456789ABCDEF",
            "https://sync.astian.org/pair?server=other#token=0123456789ABCDEF",
        )) reject(url)
    }

    @Test fun malformed_and_unrelated_qr_codes_are_rejected() {
        for (contents in listOf(
            "0123456789ABCDEF", "not a URL", "https://sync.astian.org/",
            "https://sync.astian.org/other#token=0123456789ABCDEF",
            "https://sync.astian.org/pair#token=short",
            "https://sync.astian.org/pair#token=0123456789abcdef",
            "https://sync.astian.org/pair#token=0123456789ABCDEF&token=0123456789ABCDEF",
            "https://sync.astian.org/pair#token=" + "A".repeat(512),
        )) reject(contents)
    }

    private fun reject(contents: String) {
        try { SyncPairing.parse(contents); fail("QR accepted: $contents") }
        catch (error: SyncException) { assertEquals("invalid_pairing_qr", error.code) }
    }
}
