package org.midorinext.android.diagnostics

import android.content.Context
import java.io.IOException
import java.util.Properties
import java.util.UUID

internal object GeckoBuildMetadata {
    fun proguardUuid(context: Context): String? = try {
        context.assets.open("sentry-debug-meta.properties").use { stream ->
            proguardUuid(Properties().apply { load(stream) })
        }
    } catch (_: IOException) {
        null
    }

    internal fun proguardUuid(properties: Properties): String? {
        val value = properties.getProperty("io.sentry.ProguardUuids")?.trim() ?: return null
        return try {
            UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
