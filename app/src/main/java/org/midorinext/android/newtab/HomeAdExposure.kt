package org.midorinext.android.newtab

import android.os.SystemClock
import java.util.UUID

class HomeAdExposure(val ad: HomeAdDecision, val position: Int? = null) {
    val id: String = UUID.randomUUID().toString()
    private val startedAt = SystemClock.elapsedRealtime()
    private val remaining = (ad.expiresAt - System.currentTimeMillis()).coerceIn(0, 900_000)
    var endReason = "hidden"

    fun visibleMillis(): Long = (SystemClock.elapsedRealtime() - startedAt).coerceIn(0, remaining)
}
