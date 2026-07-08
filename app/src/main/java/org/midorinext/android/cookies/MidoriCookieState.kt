package org.midorinext.android.cookies

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriCookieState @Inject constructor() {
    internal var communicationPort: WebExtension.Port? = null

    var isConnected by mutableStateOf(false)
        private set

    private val restoreChannel = Channel<Unit>(0)

    internal fun messageReceived(message: JSONObject) {
        Log.d("MIDORI_COOKIES", "Received message in native app from webextension: ${message}")

        when (message.getString("code")) {
            "user_logged" -> isConnected = message.getBoolean("value")
            "restored" -> {
                if (message.getBoolean("value")) Log.d("MIDORI_COOKIES", "Midori cookies restored.")
                else Log.e("MIDORI_COOKIES", "Failed restoring midori cookies. Unlocking zap anyway.")
                restoreChannel.trySend(Unit)
            }
            else -> {}
        }

        communicationPort?.postMessage(JSONObject().also {
            it.put("code", "message_received")
        })
    }

    suspend fun restoreCookies(then: () -> Unit) {
        val port = communicationPort
        if (port == null) {
            Log.w("MIDORI_COOKIES", "Cookie extension port unavailable. Continuing zap without restore.")
            then()
            return
        }

        port.postMessage(JSONObject().also {
            it.put("code", "restore_cookies")
        })

        val restored = withTimeoutOrNull(3000L) {
            restoreChannel.receive()
            true
        } ?: false

        if (!restored) {
            Log.w("MIDORI_COOKIES", "Cookie restore timed out. Continuing zap to avoid blocking UI.")
        }

        then()
    }
}