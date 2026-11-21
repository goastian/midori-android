package org.midorinext.android.mozac.media

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate
import org.midorinext.android.MidoriApplication
import org.midorinext.android.diagnostics.DiagnosticsCrashReporting
import javax.inject.Inject

@AndroidEntryPoint
class MediaSessionService: AbstractMediaSessionService() {
    private val browserApp get() = application as MidoriApplication
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingForegroundStop: Runnable? = null

    override fun onCreate() {
        browserApp.initializeBrowser("media_service", restoreSession = false)
        super.onCreate()
    }

    override fun onDestroy() {
        cancelPendingForegroundStop()
        super.onDestroy()
    }

    override val crashReporter: CrashReporting = object : CrashReporting by DiagnosticsCrashReporting {
        override fun submitCaughtException(throwable: Throwable): Job {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && throwable is ForegroundServiceStartNotAllowedException) {
                browserApp.retryMediaForegroundOnNextStart()
                Log.i("MidoriMedia", "Android denied foreground promotion. Will retry when the browser is visible.")
                return Job().apply { complete() }
            }
            return DiagnosticsCrashReporting.submitCaughtException(throwable)
        }
    }

    @Inject lateinit var nd: dagger.Lazy<NotificationsDelegate>
    override val notificationsDelegate: NotificationsDelegate by lazy { nd.get() }

    @Inject lateinit var s: dagger.Lazy<BrowserStore>
    override val store: BrowserStore by lazy { s.get() }

    /**
     * Keep active web media alive after the browser task is dismissed. The foreground media
     * service and its notification remain visible, so users can still pause or resume playback
     * from Android's system controls.
     */
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    private fun cancelPendingForegroundStop() {
        pendingForegroundStop?.let(mainHandler::removeCallbacks)
        pendingForegroundStop = null
    }

    companion object {
        private const val TRACK_TRANSITION_GRACE_MS = 5_000L

        @JvmStatic
        fun deferForegroundStop(service: Service, stop: Runnable, isForeground: Boolean) {
            if (service !is MediaSessionService || !isForeground || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                stop.run()
                return
            }
            if (service.pendingForegroundStop == null) {
                // Short gaps between tracks can otherwise require a new, disallowed background promotion.
                if (service.mainHandler.postDelayed(stop, TRACK_TRANSITION_GRACE_MS)) {
                    service.pendingForegroundStop = stop
                } else {
                    stop.run()
                }
            }
        }

        @JvmStatic
        fun cancelForegroundStop(service: Service) {
            (service as? MediaSessionService)?.cancelPendingForegroundStop()
        }

        @JvmStatic
        fun startCurrentForeground(service: Service, id: Int, notification: Notification, request: Int, latest: Int) {
            if (request != latest) throw CancellationException("Media foreground request was superseded")
            service.startForeground(id, notification)
        }
    }
}
