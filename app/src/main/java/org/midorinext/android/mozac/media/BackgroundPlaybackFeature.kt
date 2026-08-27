package org.midorinext.android.mozac.media

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
    private var transitionRecoveryDeadlineMillis = 0L
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
        beginTransitionRecovery()
        continuePlaybackAfterUiTransition()
    }

    /**
     * Window focus is lost just before Android pauses the activity. Starting the hand-off here
     * gives GeckoView a head start before it hides the display surface.
     */
    fun onBrowserActivityLosingFocus() {
        beginTransitionRecovery()
        continuePlaybackAfterUiTransition()
    }

    fun onBrowserActivityResumed() {
        appInBackground = false
        transitionRecoveryDeadlineMillis = 0L
        finishUiTransitionIfNeeded()
    }

    /**
     * Called before Compose disposes the GeckoView while opening the tab tray. This is separate
     * from an Activity pause, but it releases the same Gecko display surface.
     */
    fun onBrowserSurfaceHidden() {
        browserSurfaceHidden = true
        beginTransitionRecovery()
        continuePlaybackAfterUiTransition()
        scheduleContinuation(SURFACE_TRANSITION_TIMEOUT_MS) {
            browserSurfaceHidden = false
            releasePausedSessionIfUiIsVisible()
        }
    }

    fun onBrowserSurfaceVisible() {
        browserSurfaceHidden = false
        transitionRecoveryDeadlineMillis = 0L
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
                if (isTransitionRecoveryActive && prioritizedSession === session) {
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
            if (isTransitionRecoveryActive) {
                // The first play request is necessarily speculative: Gecko has not emitted its
                // paused state yet. Trigger another one at the exact state change, which avoids
                // waiting for the later retry (previously the source of a ~700 ms audible gap).
                continuePlaybackAfterUiTransition()
            } else if (!isPlaybackProtected) {
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

    private val isTransitionRecoveryActive: Boolean
        get() = isPlaybackProtected && SystemClock.uptimeMillis() < transitionRecoveryDeadlineMillis

    private fun beginTransitionRecovery() {
        transitionRecoveryDeadlineMillis = maxOf(
            transitionRecoveryDeadlineMillis,
            SystemClock.uptimeMillis() + TRANSITION_RECOVERY_WINDOW_MS,
        )
    }

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
        const val TRANSITION_RECOVERY_WINDOW_MS = 1500L
        // GeckoView generally releases its surface a few hundred milliseconds after navigation.
        // Probe that short interval closely so playback is reasserted immediately afterwards,
        // rather than leaving the user with a perceptible pause before the former 450 ms retry.
        val BACKGROUND_CONTINUATION_DELAYS_MS = longArrayOf(0L, 50L, 100L, 150L, 200L, 250L, 300L, 350L, 400L, 500L, 650L, 800L)
    }

    private data class PlayingMedia(
        val session: EngineSession,
        val controller: MediaSession.Controller,
    )
}
