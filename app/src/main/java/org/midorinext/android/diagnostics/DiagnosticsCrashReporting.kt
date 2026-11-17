package org.midorinext.android.diagnostics

import io.sentry.Sentry
import kotlinx.coroutines.Job
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.lib.crash.CrashReporter

internal object DiagnosticsCrashReporting : CrashReporting {
    @Volatile internal var reporter: CrashReporter? = null

    override fun submitCaughtException(throwable: Throwable): Job {
        Sentry.captureException(throwable) { it.setTag("diagnostics.reporter", "android_components") }
        return Job().apply { complete() }
    }

    override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) {
        reporter?.recordCrashBreadcrumb(breadcrumb)
    }
}
