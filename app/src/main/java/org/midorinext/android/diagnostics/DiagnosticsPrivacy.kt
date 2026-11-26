package org.midorinext.android.diagnostics

import io.sentry.Breadcrumb
import io.sentry.SentryBaseEvent
import io.sentry.SentryEvent
import io.sentry.protocol.SentryTransaction

internal object DiagnosticsPrivacy {
    fun predatesConsent(event: SentryEvent, consentTimestamp: Long): Boolean =
        event.timestamp.time < consentTimestamp &&
            event.exceptions.orEmpty().any { it.mechanism?.type == "AppExitInfo" }

    fun <T : SentryBaseEvent> scrub(event: T): T {
        if (event is SentryEvent) DiagnosticsRuntime.restoreNativeSnapshot(event)
        event.user = null
        event.request = null
        event.extras = null
        event.breadcrumbs = event.breadcrumbs?.mapNotNull(::breadcrumb)
        if (event is SentryEvent) {
            DiagnosticsDescriptions.enrich(event)
        }
        if (event is SentryTransaction) {
            event.spans.forEach { span ->
                span.data = null
            }
        }
        return event
    }

    fun breadcrumb(breadcrumb: Breadcrumb): Breadcrumb? {
        if (breadcrumb.category !in setOf("app.lifecycle", "navigation", "device.event", "midori.startup", "midori.memory", "midori.gecko")) return null
        val safe = Breadcrumb(breadcrumb.timestamp).apply {
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
            "midori.startup" -> safe.message = breadcrumb.message?.takeIf { it in DiagnosticsRuntime.startupStages }
            "midori.memory" -> (breadcrumb.getData("trim_level") as? Number)?.toInt()?.takeIf { it in 0..100 }
                ?.let { safe.setData("trim_level", it) }
            "midori.gecko" -> safe.message = breadcrumb.message?.takeIf { it in setOf("content_crashed", "content_recovered") }
        }
        return safe
    }
}
