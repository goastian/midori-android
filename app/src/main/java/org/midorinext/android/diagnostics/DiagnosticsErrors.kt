package org.midorinext.android.diagnostics

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.SentryException
import kotlinx.coroutines.CancellationException

internal enum class DiagnosticOperation {
    AUTOFILL_FETCH,
    AUTOFILL_SAVE,
    PASSWORD_SELECT,
    PASSWORD_VALIDATE,
    PASSWORD_SAVE,
    SAVED_DATA_LOAD,
    SAVED_DATA_DELETE,
    SAVED_CARD_ADD,
    SAVED_ADDRESS_ADD,
    SYNC,
    DOWNLOAD,
}

internal object DiagnosticsErrors {
    fun capture(error: Throwable, operation: DiagnosticOperation) {
        if (error is CancellationException || !Sentry.isEnabled()) return
        Sentry.captureEvent(SentryEvent(error).apply {
            setTag("diagnostics.reporter", "midori_handled")
            setTag("midori.operation", operation.name.lowercase(java.util.Locale.ROOT))
        })
    }

    fun downloadFailed() {
        if (!Sentry.isEnabled()) return
        Sentry.captureEvent(SentryEvent().apply {
            level = SentryLevel.WARNING
            exceptions = listOf(SentryException().apply {
                type = "DownloadFailed"
                module = "org.midorinext.android.mozac.downloads"
            })
            setTag("diagnostics.reporter", "midori_handled")
            setTag("midori.operation", "download")
        })
    }
}
