package org.midorinext.android.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.ProfileLifecycle
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.transport.ITransportGate
import io.sentry.util.HintUtils
import mozilla.components.lib.crash.CrashReporter
import mozilla.components.lib.crash.RuntimeTagProvider
import mozilla.components.lib.crash.sentry.eventprocessors.AddMechanismEventProcessor
import mozilla.components.lib.crash.sentry.eventprocessors.RustCrashEventProcessor
import mozilla.components.support.ktx.android.content.isMainProcess
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.midorinext.android.BuildConfig
import org.midorinext.android.R

data class DiagnosticsConsent(val enabled: Boolean = true, val answered: Boolean = false) {
    val canSend: Boolean get() = enabled && answered
}

@Singleton
class DiagnosticsController @Inject constructor(@ApplicationContext private val context: Context) {
    private val preferences = context.getSharedPreferences("stability_diagnostics", Context.MODE_PRIVATE)
    private val mutableConsent = MutableStateFlow(DiagnosticsConsent(
        enabled = preferences.getBoolean("enabled", true),
        answered = preferences.getBoolean("answered", false),
    ))
    val consent = mutableConsent.asStateFlow()
    private val allowed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val processExits = DiagnosticsProcessExits(context, preferences)
    private val mainProcess = context.isMainProcess()
    private val consentFile = File(context.noBackupFilesDir, "stability_diagnostics.enabled")
    internal val cacheDirectory = File(context.noBackupFilesDir, "stability_diagnostics")
    private val crashReporter by lazy {
        CrashReporter(
            context = context,
            services = listOf(DiagnosticsSentryService(::prepareCrashReport)),
            enabled = if (mainProcess) consent.value.canSend else consentFile.isFile,
            shouldPrompt = CrashReporter.Prompt.NEVER,
            promptConfiguration = CrashReporter.PromptConfiguration(
                appName = context.getString(R.string.app_name),
                organizationName = context.getString(R.string.app_name),
            ),
            runtimeTagProviders = listOf(object : RuntimeTagProvider {
                override fun invoke(): Map<String, String> = if (mainProcess && canSend()) {
                    mapOf("midori_runtime_v2" to DiagnosticsRuntime.crashSnapshot())
                } else emptyMap()
            }),
        )
    }

    internal fun prepareGeckoCrashReporter(): CrashReporter = crashReporter.also {
        if (mainProcess) DiagnosticsCrashReporting.reporter = it
    }

    fun start() {
        prepareGeckoCrashReporter()
        if (consent.value.canSend) {
            if (enableSending()) initialize()
        } else scope.launch {
            mutex.withLock {
                if (!consent.value.canSend) {
                    consentFile.delete()
                    processExits.clear()
                    cacheDirectory.deleteRecursively()
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean): Job {
        if (!enabled) allowed.set(false)
        return scope.launch {
            mutex.withLock {
                allowed.set(false)
                if (!enabled) {
                    consentFile.delete()
                    crashReporter.enabled = false
                }
                check(preferences.edit().putBoolean("enabled", enabled).putBoolean("answered", true).commit())
                mutableConsent.value = DiagnosticsConsent(enabled, answered = true)
                crashReporter.enabled = enabled
                if (enabled) {
                    if (enableSending()) {
                        if (Sentry.isEnabled()) allowed.set(true) else initialize()
                    }
                } else {
                    Sentry.close()
                    DiagnosticsRuntime.clear()
                    processExits.clear()
                    cacheDirectory.deleteRecursively()
                }
            }
        }
    }

    @Synchronized
    private fun initialize() {
        if (Sentry.isEnabled()) return
        allowed.set(true)
        SentryAndroid.init(context) { options ->
            options.isEnabled = true
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.dist = BuildConfig.VERSION_CODE.toString()
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.isDebug = BuildConfig.DEBUG
            options.cacheDirPath = if (mainProcess) cacheDirectory.absolutePath else File(cacheDirectory, "gecko").absolutePath
            options.maxCacheItems = 20
            options.maxBreadcrumbs = 40
            options.connectionTimeoutMillis = 5_000
            options.readTimeoutMillis = 5_000
            options.shutdownTimeoutMillis = 1_000
            options.isSendDefaultPii = false
            options.dataCollection.userInfo = false
            options.dataCollection.cookies = KeyValueCollectionBehavior.off()
            options.dataCollection.urlQueryParams = KeyValueCollectionBehavior.off()
            options.dataCollection.httpBodies = emptySet()
            options.dataCollection.httpHeaders.request = KeyValueCollectionBehavior.off()
            options.dataCollection.httpHeaders.response = KeyValueCollectionBehavior.off()
            options.dataCollection.filePaths = false
            options.dataCollection.databaseQueryData = false
            options.setTracePropagationTargets(emptyList())
            options.isAttachScreenshot = false
            options.isAttachViewHierarchy = false
            options.isEnableUserInteractionBreadcrumbs = false
            options.isEnableUserInteractionTracing = false
            options.isEnableSystemEventBreadcrumbs = false
            options.isEnableNetworkEventBreadcrumbs = false
            options.setEnableUncaughtExceptionHandler(mainProcess)
            options.isAttachThreads = mainProcess
            options.isAttachStacktrace = mainProcess
            options.isEnableActivityLifecycleBreadcrumbs = mainProcess
            options.isEnableAppLifecycleBreadcrumbs = mainProcess
            options.isEnableAppComponentBreadcrumbs = mainProcess
            options.isEnableAutoActivityLifecycleTracing = mainProcess
            options.isEnableTimeToFullDisplayTracing = mainProcess
            options.isEnableFramesTracking = mainProcess
            options.isEnableAutoSessionTracking = mainProcess
            options.isAnrEnabled = mainProcess
            options.isEnableNdk = mainProcess
            options.isEnableScopeSync = true
            options.tracesSampleRate = if (!mainProcess) 0.0 else if (BuildConfig.DEBUG) 1.0 else 0.1
            options.profileSessionSampleRate = if (!mainProcess) 0.0 else if (BuildConfig.DEBUG) 1.0 else 0.05
            options.profileLifecycle = ProfileLifecycle.TRACE
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            options.logs.isEnabled = false
            options.addInAppInclude("org.midorinext.android")
            options.setTag("app.version", BuildConfig.VERSION_NAME)
            options.setTag("app.build", BuildConfig.VERSION_CODE.toString())
            options.setTag("app.variant", BuildConfig.FLAVOR)
            options.setTag("gecko.version", org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION)
            options.setTag("diagnostics.process_role", if (mainProcess) "browser" else "crash_reporter")
            options.addEventProcessor(RustCrashEventProcessor())
            options.addEventProcessor(AddMechanismEventProcessor())
            options.beforeSend = SentryOptions.BeforeSendCallback { event, hint ->
                if (canSend()) {
                    if (mainProcess) processExits.enrich(event)
                    DiagnosticsPrivacy.scrub(event).also {
                        if (mainProcess && HintUtils.shouldApplyScopeData(hint)) DiagnosticsRuntime.enrich(it)
                    }
                } else null
            }
            options.beforeSendTransaction = SentryOptions.BeforeSendTransactionCallback { event, _ ->
                if (canSend()) DiagnosticsPrivacy.scrub(event) else null
            }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                if (canSend()) DiagnosticsPrivacy.breadcrumb(breadcrumb) else null
            }
            val connectivity = options.transportGate
            options.setTransportGate(ITransportGate { canSend() && connectivity.isConnected })
        }
        if (mainProcess) DiagnosticsRuntime.start()
        if (mainProcess) scope.launch {
            mutex.withLock {
                if (allowed.get()) processExits.startSession()
            }
        }
    }

    private fun canSend(): Boolean = allowed.get() && consentFile.isFile

    private fun enableSending(): Boolean = try {
        consentFile.createNewFile()
        true
    } catch (error: java.io.IOException) {
        allowed.set(false)
        crashReporter.enabled = false
        android.util.Log.w("MidoriDiagnostics", "Cannot persist diagnostics consent", error)
        false
    }

    private fun prepareCrashReport(): Boolean {
        if (!consentFile.isFile) return false
        initialize()
        return canSend()
    }
}
