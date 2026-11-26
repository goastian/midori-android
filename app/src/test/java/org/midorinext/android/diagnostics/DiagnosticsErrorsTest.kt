package org.midorinext.android.diagnostics

import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.protocol.Mechanism
import io.sentry.protocol.SentryException
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiagnosticsErrorsTest {
    private val events = mutableListOf<SentryEvent>()

    @Before
    fun prepareLocalCapture() {
        Sentry.init { options ->
            options.dsn = "https://public@example.invalid/1"
            options.setEnableUncaughtExceptionHandler(false)
            options.isEnableShutdownHook = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                events += DiagnosticsPrivacy.scrub(event)
                null
            }
        }
    }

    @After
    fun close() = Sentry.close()

    @Test
    fun handledStorageFailuresRetainTheirStackAndOperationWithoutPrivateText() {
        DiagnosticsErrors.capture(
            IllegalStateException("password=secret cvv=001 https://example.invalid/account"),
            DiagnosticOperation.AUTOFILL_SAVE,
        )
        val event = events.single()
        assertEquals("autofill_save", event.getTag("midori.operation"))
        assertEquals("midori_handled", event.getTag("diagnostics.reporter"))
        assertTrue(event.exceptions!!.single().stacktrace!!.frames!!.isNotEmpty())
        assertFalse(event.exceptions!!.single().value!!.contains("secret"))
        assertFalse(event.message!!.formatted!!.contains("001"))
        assertNull(event.extras)
        assertNull(event.request)
        assertNull(event.user)
        assertEquals(mapOf("operation" to "autofill_save", "recoverable" to true), event.contexts["midori_operation"])
    }

    @Test
    fun cancellationAndDisabledDiagnosticsDoNotGenerateReports() {
        DiagnosticsErrors.capture(CancellationException(), DiagnosticOperation.SYNC)
        Sentry.close()
        DiagnosticsErrors.capture(IllegalStateException(), DiagnosticOperation.SYNC)
        assertTrue(events.isEmpty())
    }

    @Test
    fun historicalAnrsAreLimitedToTheCurrentConsentPeriod() {
        val event = SentryEvent().apply {
            timestamp = java.util.Date(1_000)
            exceptions = listOf(SentryException().apply {
                mechanism = Mechanism().apply { type = "AppExitInfo" }
            })
        }
        assertTrue(DiagnosticsPrivacy.predatesConsent(event, 2_000))
        assertFalse(DiagnosticsPrivacy.predatesConsent(event, 500))
        DiagnosticsPrivacy.scrub(event)
        assertEquals("anr", event.getTag("diagnostics.source"))
    }
}
