package org.midorinext.android.diagnostics

import io.sentry.Breadcrumb
import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent
import io.sentry.protocol.SentryTransaction

internal object DiagnosticsPrivacy {
    fun <T : SentryBaseEvent> scrub(event: T): T {
        event.user = null
        event.request = null
        event.extras = null
        event.breadcrumbs = event.breadcrumbs?.mapNotNull(::breadcrumb)
        if (event is SentryEvent) {
            event.message = null
            event.exceptions?.forEach { it.value = null }
        }
        if (event is SentryTransaction) {
            event.spans.forEach { span ->
                span.data = null
            }
        }
        return event
    }

    fun breadcrumb(breadcrumb: Breadcrumb): Breadcrumb? {
        if (breadcrumb.category !in setOf("app.lifecycle", "navigation", "device.event", "midori.startup")) return null
        val safe = Breadcrumb().apply {
            category = breadcrumb.category
            type = breadcrumb.type
            level = breadcrumb.level
        }
        when (breadcrumb.category) {
            "app.lifecycle" -> breadcrumb.getData("state")?.toString()?.takeIf { it in setOf("foreground", "background") }
                ?.let { safe.setData("state", it) }
            "navigation" -> breadcrumb.getData("state")?.toString()?.takeIf {
                it in setOf("created", "started", "resumed", "paused", "stopped", "destroyed", "saveInstanceState")
            }?.let { safe.setData("state", it) }
            "device.event" -> if (breadcrumb.getData("action") == "LOW_MEMORY") safe.setData("action", "LOW_MEMORY")
            "midori.startup" -> safe.message = breadcrumb.message?.takeIf { it.matches(Regex("[a-z_]+")) }
        }
        return safe
    }
}
