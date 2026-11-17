package org.midorinext.android.diagnostics

import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.SentryException

internal object DiagnosticsDescriptions {
    fun enrich(event: SentryEvent) {
        val exceptions = event.exceptions.orEmpty()
        exceptions.forEach { it.value = describe(it) }
        val exception = exceptions.lastOrNull()
        val source = when {
            event.getTag("diagnostics.reporter") == "mozilla_crash" && event.platform == "native" -> "gecko_native"
            exception?.mechanism?.type == "android.process.exit" -> "process_exit"
            exception?.type == "GeckoContentProcessCrash" -> "gecko_content"
            event.platform == "native" || exception?.type?.startsWith("SIG") == true -> "native"
            exception?.type?.contains("ApplicationNotResponding") == true -> "anr"
            else -> "jvm"
        }
        val description = exception?.value ?: if (source == "gecko_native") {
            "GeckoView reported a native process crash. Process visibility and type are attached; this event does not include a symbolicated native stack."
        } else if (source == "native") {
            "Native crash captured. Inspect the symbolicated minidump and Android process exit context; a kill frame alone does not identify the cause."
        } else {
            "Diagnostic event without an exception. Inspect the technical context."
        }
        event.message = Message().apply { formatted = description }
        event.setTag("diagnostics.source", source)
        event.setTag("diagnostics.schema", "2")
        event.contexts["midori_error"] = mapOf("source" to source, "summary" to description)
    }

    private fun describe(exception: SentryException): String = when (exception.type) {
        "SIGABRT" -> "Native process aborted (SIGABRT). Inspect the crashing thread and its callers; kill/pthread_kill is a termination frame."
        "SIGSEGV" -> "Native code accessed invalid memory (SIGSEGV). Inspect the symbolicated crashing thread."
        "SIGBUS" -> "Native memory access failed (SIGBUS). Inspect the symbolicated crashing thread."
        "SIGILL" -> "Native code executed an illegal instruction (SIGILL)."
        "SIGFPE" -> "Native arithmetic fault (SIGFPE)."
        "SIGKILL", "AndroidProcessSigkill" -> "Process terminated by SIGKILL. The signal alone does not establish why it was killed."
        "AndroidLowMemoryKill" -> "Android reports that it terminated the previous browser process due to low memory. This is not a Java OutOfMemoryError."
        "AndroidResourceLimit" -> "Android reports that it terminated the previous browser process for excessive resource usage."
        "AndroidInitializationFailure" -> "Android reports that the previous browser process failed to initialize."
        "GeckoContentProcessCrash" -> "Gecko reported a crashed content process. The browser process may still be running; this notification does not include a native stack trace."
        "OutOfMemoryError" -> {
            val allocation = exception.value?.let {
                Regex("^Failed to allocate a ([0-9]{1,12}) byte allocation with ([0-9]{1,12}) free bytes").find(it)
                    ?: Regex("^Java memory allocation failed: requested ([0-9]{1,12}) bytes with ([0-9]{1,12}) free bytes\\.$").matchEntire(it)
            }
            if (allocation == null) "Java memory allocation failed (OutOfMemoryError)."
            else "Java memory allocation failed: requested ${allocation.groupValues[1]} bytes with ${allocation.groupValues[2]} free bytes."
        }
        "NullPointerException" -> "Code attempted to use a null reference. Inspect the exception stack and cause chain."
        "IllegalStateException" -> "An operation ran in an invalid state. Inspect the exception stack and lifecycle context."
        "IllegalArgumentException" -> "An operation received an invalid argument. Argument values are omitted for privacy."
        "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException" -> "Code accessed an index outside the available range."
        "StackOverflowError" -> "The Java thread stack was exhausted. Inspect the stack for repeated calls."
        "UnsatisfiedLinkError" -> "A native library or native method could not be linked. Inspect the build, ABI and stack trace."
        "ApplicationNotResponding", "ApplicationNotRespondingException" -> "The application stopped responding (ANR). Inspect the blocked thread and lifecycle context."
        "SocketTimeoutException" -> "A network operation exceeded its timeout. The remote address is omitted for privacy."
        "UnknownHostException" -> "A network host could not be resolved. The host name is omitted for privacy."
        "SentryTestCrashException" -> "Deliberate debug integration crash."
        else -> "Exception captured. Inspect its type, cause chain and stack trace; free-form text is omitted for privacy."
    }
}
