package org.midorinext.android.mozac

import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoRuntimeSettings

internal object Gecko161Features {
    private val securityCode = publicStringMethod(Autocomplete.CreditCard.Builder::class.java, "securityCode")
    private val proguardUuid = publicStringMethod(GeckoRuntimeSettings.Builder::class.java, "proguardUuid")
    val supportsSecurityCode: Boolean get() = securityCode != null
    val supportsProguardUuid: Boolean get() = proguardUuid != null

    fun securityCode(builder: Autocomplete.CreditCard.Builder, code: String?): Autocomplete.CreditCard.Builder {
        securityCode?.invoke(builder, code)
        return builder
    }

    fun proguardUuid(builder: GeckoRuntimeSettings.Builder, uuid: String?) {
        if (uuid != null) proguardUuid?.invoke(builder, uuid)
    }

    private fun publicStringMethod(type: Class<*>, name: String) = try {
        type.getMethod(name, String::class.java)
    } catch (_: NoSuchMethodException) {
        null
    }
}
