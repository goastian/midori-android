package org.midorinext.android.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SyncVault @Inject constructor(@ApplicationContext context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "midori-sync-v1"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        requireSync(!file.baseFile.exists(), "local_keys_unavailable")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(): JSONObject {
        if (!file.baseFile.exists()) return obj("version" to 1)
        val bytes = file.readFully()
        requireSync(bytes.size in 29..MAX_BYTES && bytes[0] == 1.toByte(), "invalid_local_state")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        cipher.updateAAD(ALIAS.toByteArray())
        val plaintext = cipher.doFinal(bytes, 13, bytes.size - 13)
        return try { JSONObject(plaintext.toString(Charsets.UTF_8)).also { requireSync(it.getInt("version") == 1, "invalid_local_state") } }
        finally { plaintext.fill(0) }
    }
    @Synchronized fun write(value: JSONObject) {
        val plaintext = value.toString().toByteArray()
        requireSync(plaintext.size <= MAX_BYTES - 29, "local_storage_full")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(ALIAS.toByteArray())
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        val stream = file.startWrite()
        try {
            stream.write(byteArrayOf(1) + cipher.iv + encrypted)
            file.finishWrite(stream)
        } catch (error: Throwable) { file.failWrite(stream); throw error }
    }
    private companion object { const val ALIAS = "MidoriSync/local/v1"; const val MAX_BYTES = 64 * 1024 * 1024 }
}
