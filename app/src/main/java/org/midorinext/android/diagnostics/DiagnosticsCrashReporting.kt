package org.midorinext.android.diagnostics

import io.sentry.Sentry
import kotlinx.coroutines.Job
import mozilla.components.concept.base.crash.Breadcrumb
import mozilla.components.concept.base.crash.CrashReporting

internal object DiagnosticsCrashReporting : CrashReporting {
    override fun submitCaughtException(throwable: Throwable): Job {
        Sentry.captureException(throwable)
        return Job().apply { complete() }
    }

    override fun recordCrashBreadcrumb(breadcrumb: Breadcrumb) = Unit
}
