package org.midorinext.android.diagnostics

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.ProfileLifecycle
import io.sentry.KeyValueCollectionBehavior
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.transport.ITransportGate
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
    internal val cacheDirectory = File(context.noBackupFilesDir, "stability_diagnostics")

    fun start() {
        if (consent.value.canSend) initialize() else scope.launch {
            mutex.withLock {
                if (!consent.value.canSend) cacheDirectory.deleteRecursively()
            }
        }
    }

    fun setEnabled(enabled: Boolean): Job {
        if (!enabled) allowed.set(false)
        return scope.launch {
            mutex.withLock {
                allowed.set(false)
                check(preferences.edit().putBoolean("enabled", enabled).putBoolean("answered", true).commit())
                mutableConsent.value = DiagnosticsConsent(enabled, answered = true)
                if (enabled) {
                    if (Sentry.isEnabled()) allowed.set(true) else initialize()
                } else {
                    Sentry.close()
                    cacheDirectory.deleteRecursively()
                }
            }
        }
    }

    private fun initialize() {
        allowed.set(true)
        SentryAndroid.init(context) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.release = "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.dist = BuildConfig.VERSION_CODE.toString()
            options.environment = if (BuildConfig.DEBUG) "development" else "production"
            options.isDebug = BuildConfig.DEBUG
            options.cacheDirPath = cacheDirectory.absolutePath
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
            options.isEnableAutoActivityLifecycleTracing = true
            options.isEnableTimeToFullDisplayTracing = true
            options.isEnableFramesTracking = true
            options.isEnableAutoSessionTracking = true
            options.isAnrEnabled = true
            options.isEnableNdk = true
            options.isEnableScopeSync = false
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.1
            options.profileSessionSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.05
            options.profileLifecycle = ProfileLifecycle.TRACE
            options.sessionReplay.sessionSampleRate = 0.0
            options.sessionReplay.onErrorSampleRate = 0.0
            options.logs.isEnabled = false
            options.addInAppInclude("org.midorinext.android")
            options.setTag("app.version", BuildConfig.VERSION_NAME)
            options.setTag("app.build", BuildConfig.VERSION_CODE.toString())
            options.setTag("app.variant", BuildConfig.FLAVOR)
            options.setTag("gecko.version", org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION)
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (allowed.get()) DiagnosticsPrivacy.scrub(event) else null
            }
            options.beforeSendTransaction = SentryOptions.BeforeSendTransactionCallback { event, _ ->
                if (allowed.get()) DiagnosticsPrivacy.scrub(event) else null
            }
            options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                if (allowed.get()) DiagnosticsPrivacy.breadcrumb(breadcrumb) else null
            }
            val connectivity = options.transportGate
            options.setTransportGate(ITransportGate { allowed.get() && connectivity.isConnected })
        }
    }
}
