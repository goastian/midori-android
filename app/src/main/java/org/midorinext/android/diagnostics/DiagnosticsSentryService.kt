package org.midorinext.android.diagnostics

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.SentryId
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.lib.crash.Crash
import mozilla.components.lib.crash.service.CrashReporterService
import java.util.Date

internal class DiagnosticsSentryService(
    private val prepareReport: () -> Boolean,
) : CrashReporterService {
    override val id = "midori-sentry"
    override val name = "Midori diagnostics"

    override fun createCrashReportUrl(identifier: String): String? = null

    override fun report(crash: Crash.UncaughtExceptionCrash): String? {
        if (!prepareReport()) return null
        return capture(SentryEvent(crash.throwable).apply {
            eventId = SentryId(crash.uuid)
            timestamp = Date(crash.timestamp)
            level = SentryLevel.FATAL
            crash.runtimeTags["midori_runtime_v2"]?.let { setExtra("midori_runtime_v2", it) }
            breadcrumbs = crash.breadcrumbs.mapNotNull(::safeBreadcrumb)
        })
    }

    override fun report(crash: Crash.NativeCodeCrash): String? {
        if (!prepareReport()) return null
        return capture(SentryEvent().apply {
            eventId = SentryId(crash.uuid)
            timestamp = Date(crash.timestamp)
            platform = "native"
            level = if (crash.isFatal) SentryLevel.FATAL else SentryLevel.ERROR
            setTag("gecko.process_visibility", crash.processVisibility?.takeIf {
                it in setOf("MAIN", "FOREGROUND_CHILD", "BACKGROUND_CHILD")
            } ?: "unknown")
            setTag("gecko.process_type", crash.processType?.takeIf {
                it in setOf("main", "content", "gpu", "rdd", "socket", "utility", "gmplugin")
            } ?: "unknown")
        })
    }

    override fun report(throwable: Throwable, breadcrumbs: ArrayList<Breadcrumb>): String? {
        if (!prepareReport()) return null
        return capture(SentryEvent(throwable).apply {
            this.breadcrumbs = breadcrumbs.mapNotNull(::safeBreadcrumb)
        })
    }

    private fun safeBreadcrumb(breadcrumb: Breadcrumb): io.sentry.Breadcrumb? = DiagnosticsPrivacy.breadcrumb(
        io.sentry.Breadcrumb(breadcrumb.date).apply {
            category = breadcrumb.category
            message = breadcrumb.message
            type = breadcrumb.type.value
            breadcrumb.data.forEach { (key, value) -> setData(key, value) }
        },
    )

    private fun capture(event: SentryEvent): String? {
        event.setTag("diagnostics.reporter", "mozilla_crash")
        event.threads = emptyList()
        return Sentry.captureEvent(event).takeUnless { it == SentryId.EMPTY_ID }?.toString()
    }
}
