package org.midorinext.android.mozac.media

import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.base.crash.CrashReporting
import mozilla.components.feature.media.service.AbstractMediaSessionService
import mozilla.components.support.base.android.NotificationsDelegate
import javax.inject.Inject

@AndroidEntryPoint
class MediaSessionService: AbstractMediaSessionService() {
    override val crashReporter: CrashReporting? = null

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
}
