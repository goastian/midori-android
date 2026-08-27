package org.midorinext.android.mozac.media

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.mediasession.MediaSession
import mozilla.components.feature.media.ext.findActiveMediaTab
import mozilla.components.lib.state.ext.flowScoped

/**
 * Keeps the session that is playing media resilient while the browser UI is not visible.
 *
 * GeckoView releases its display as soon as the activity is covered or the device is locked.
 * Raising the priority only for an actively playing session prevents that session from being
 * discarded under memory pressure. The Android media service continues to own audio focus and
 * the playback notification.
 */
class BackgroundPlaybackFeature(
    private val store: BrowserStore,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var prioritizedSession: EngineSession? = null
    private var prioritizedController: MediaSession.Controller? = null
    private var appInBackground = false
    private var browserSurfaceHidden = false
    private var playbackWasReportedActive = false
    private val continuationTasks = mutableListOf<Runnable>()

    fun start() {
        if (scope != null) return

        scope = store.flowScoped(dispatcher = Dispatchers.Main.immediate) { flow ->
            flow.map { state ->
                state.findActiveMediaTab()
                    ?.takeIf {
                        it.mediaSessionState?.playbackState == MediaSession.PlaybackState.PLAYING
                    }
                    ?.let { tab ->
                        tab.engineState.engineSession?.let { session ->
                            PlayingMedia(
                                session = session,
                                controller = tab.mediaSessionState!!.controller,
                            )
                        }
                    }
            }
                .distinctUntilChanged()
                .collect(::updatePlaybackSession)
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        cancelContinuationTasks()
        updateSessionPriority(null, null)
    }

    /**
     * Must be called from the browser Activity before it releases the GeckoView surface.
     * ProcessLifecycleOwner is deliberately not used here: its background callback is delayed.
     */
    fun onBrowserActivityPaused() {
        appInBackground = true
        continuePlaybackAfterUiTransition()
    }

    fun onBrowserActivityResumed() {
        appInBackground = false
        finishUiTransitionIfNeeded()
    }

    /**
     * Called before Compose disposes the GeckoView while opening the tab tray. This is separate
     * from an Activity pause, but it releases the same Gecko display surface.
     */
    fun onBrowserSurfaceHidden() {
        browserSurfaceHidden = true
        continuePlaybackAfterUiTransition()
        scheduleContinuation(SURFACE_TRANSITION_TIMEOUT_MS) {
            browserSurfaceHidden = false
            releasePausedSessionIfUiIsVisible()
        }
    }

    fun onBrowserSurfaceVisible() {
        browserSurfaceHidden = false
        finishUiTransitionIfNeeded()
    }

    /**
     * GeckoView marks a session inactive after its display is released. This happens when the
     * browser screen is replaced by the tab tray and when Android backgrounds the activity.
     * Keep only the session that is currently playing active so its media session can continue.
     */
    fun reassertPlayingSession() {
        val playingSession = prioritizedSession ?: return
        keepSessionActive(playingSession, 0L)
        // GeckoView releases the display asynchronously, after the activity's lifecycle event.
        // Repeat after that release has settled so it cannot mark the playing session inactive.
        keepSessionActive(playingSession, SURFACE_RELEASE_SETTLE_DELAY_MS)
    }

    private fun keepSessionActive(session: EngineSession, delayMillis: Long) {
        mainHandler.postDelayed({
            if (prioritizedSession === session) {
                GeckoBackgroundPlaybackController.keepActive(session)
            }
        }, delayMillis)
    }

    /**
     * GeckoView may report a transient pause while it replaces the Activity surface. Keep the
     * user-initiated media session alive during that short transition and resume it through the
     * site's normal Media Session controller, just as Android's playback notification does.
     */
    private fun continuePlaybackAfterUiTransition() {
        val session = prioritizedSession ?: return
        val controller = prioritizedController ?: return

        BACKGROUND_CONTINUATION_DELAYS_MS.forEach { delayMillis ->
            scheduleContinuation(delayMillis) {
                if (isPlaybackProtected && prioritizedSession === session) {
                    GeckoBackgroundPlaybackController.keepActive(session)
                    controller.play()
                }
            }
        }
    }

    private fun scheduleContinuation(delayMillis: Long, action: () -> Unit) {
        lateinit var task: Runnable
        task = Runnable {
            continuationTasks.removeIf { it === task }
            action()
        }
        continuationTasks += task
        mainHandler.postDelayed(task, delayMillis)
    }

    private fun cancelContinuationTasks() {
        continuationTasks.forEach(mainHandler::removeCallbacks)
        continuationTasks.clear()
    }

    private fun updatePlaybackSession(playingMedia: PlayingMedia?) {
        if (playingMedia != null) {
            playbackWasReportedActive = true
            updateSessionPriority(playingMedia.session, playingMedia.controller)
        } else {
            playbackWasReportedActive = false
            if (!isPlaybackProtected) {
                updateSessionPriority(null, null)
            }
        }
    }

    private fun finishUiTransitionIfNeeded() {
        if (!isPlaybackProtected) {
            cancelContinuationTasks()
            releasePausedSessionIfUiIsVisible()
        }
    }

    private fun releasePausedSessionIfUiIsVisible() {
        if (!isPlaybackProtected && !playbackWasReportedActive) {
            updateSessionPriority(null, null)
        }
    }

    private val isPlaybackProtected: Boolean
        get() = appInBackground || browserSurfaceHidden

    private fun updateSessionPriority(
        session: EngineSession?,
        controller: MediaSession.Controller?,
    ) {
        if (prioritizedSession === session) return

        prioritizedSession?.updateSessionPriority(EngineSession.SessionPriority.DEFAULT)
        session?.updateSessionPriority(EngineSession.SessionPriority.HIGH)
        prioritizedSession = session
        prioritizedController = controller
        reassertPlayingSession()
    }

    private companion object {
        const val SURFACE_RELEASE_SETTLE_DELAY_MS = 750L
        const val SURFACE_TRANSITION_TIMEOUT_MS = 2400L
        val BACKGROUND_CONTINUATION_DELAYS_MS = longArrayOf(450L, 1000L, 1800L)
    }

    private data class PlayingMedia(
        val session: EngineSession,
        val controller: MediaSession.Controller,
    )
}
