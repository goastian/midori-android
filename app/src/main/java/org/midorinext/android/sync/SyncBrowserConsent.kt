package org.midorinext.android.sync

internal object SyncBrowserConsent {
    const val VERSION = 2

    fun code(state: String, challenge: String, redirect: String, deviceType: String): String = shortCode(
        "MidoriSync/browser-consent/v2", state, challenge, redirect, deviceType)

    fun pairingCode(server: String, token: String): String = shortCode(
        "MidoriSync/pairing-consent/v1", server, token)

    private fun shortCode(vararg fields: String): String {
        val binding = fields.joinToString(",", "[", "]") { value ->
            require(value.all { it.code in 0x20..0x7e && it != '"' && it != '\\' })
            "\"$value\""
        }
        val code = digest(binding.toByteArray(Charsets.UTF_8)).take(5).joinToString("") { "%02X".format(it.toInt() and 255) }
        return code.take(5) + "-" + code.drop(5)
    }
}
