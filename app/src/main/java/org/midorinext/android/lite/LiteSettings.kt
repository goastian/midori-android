package org.midorinext.android.lite

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import org.midorinext.android.BuildConfig
import java.io.File
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal enum class LiteCompressionProfile(val id: String, val requestSiteSavings: Boolean = false) {
    LOSSLESS("lossless"),
    BALANCED("balanced"),
    SAVER("saver", requestSiteSavings = true),
    EXTREME("extreme", requestSiteSavings = true),
}

internal data class LitePreference(
    val server: LiteSettings? = null,
    val enabled: Boolean = false,
    val compression: LiteCompressionProfile = LiteCompressionProfile.SAVER,
)

internal class LiteSettings(
    val server: URI,
    val deviceId: String,
    val token: String,
) {
    val port: Int get() = server.port.takeIf { it > 0 } ?: if (server.scheme == "https") 443 else 80

    companion object {
        fun origin(address: String): URI? = endpoint(address)?.takeIf { it.rawPath in setOf("", "/") }
            ?.let { URI(it.scheme, null, it.host, it.port, null, null, null) }

        fun enrollment(address: String): URI? = endpoint(address)?.takeIf { it.rawPath == "/api/v1/lite/register" }

        private fun endpoint(address: String): URI? {
            val uri = runCatching { URI(address.trim()) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            val secure = uri.scheme == "https"
            val localDebug = BuildConfig.DEBUG && uri.scheme == "http" && host in setOf("localhost", "127.0.0.1")
            return uri.takeIf {
                (secure || localDebug) && it.rawUserInfo == null && it.rawQuery == null && it.rawFragment == null &&
                    (it.port == -1 || it.port in 1..65535)
            }
        }

        fun parse(address: String, deviceId: String, token: String): LiteSettings? {
            val uri = origin(address) ?: return null
            val id = deviceId.trim()
            val secret = token.trim()
            if (!id.matches(Regex("[A-Za-z0-9._-]{1,128}")) || !secret.matches(Regex("[A-Za-z0-9._~+/-]{1,256}={0,2}"))) {
                return null
            }
            return LiteSettings(uri, id, secret)
        }
    }
}

internal class LiteSettingsStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "midori-lite-v1"))

    fun read(): LitePreference {
        val bytes = try {
            file.readFully()
        } catch (_: java.io.FileNotFoundException) {
            return LitePreference()
        }
        require(bytes.size in 29..4096 && bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        val decoded = cipher.doFinal(bytes, 13, bytes.size - 13)
        try {
            val value = JSONObject(decoded.toString(Charsets.UTF_8))
            val settings = LiteSettings.parse(value.optString("server"), value.optString("device"), value.optString("token"))
            val profileId = value.optString("compression")
            val profile = LiteCompressionProfile.entries.firstOrNull { it.id == profileId }
            return LitePreference(settings, value.getBoolean("enabled"), profile ?: LiteCompressionProfile.SAVER)
        } finally {
            decoded.fill(0)
        }
    }

    fun write(preference: LitePreference) {
        val settings = preference.server
        val bytes = JSONObject()
            .put("server", settings?.server?.toString())
            .put("device", settings?.deviceId)
            .put("token", settings?.token)
            .put("enabled", preference.enabled)
            .put("compression", preference.compression.id)
            .toString().toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = try { cipher.doFinal(bytes) } finally { bytes.fill(0) }
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(1) + cipher.iv + encrypted)
            file.finishWrite(stream)
        } catch (error: Throwable) {
            file.failWrite(stream)
            throw error
        }
    }

    private fun key(): SecretKey {
        val alias = "MidoriLite/local/v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
        }.generateKey()
    }
}
