package org.midorinext.android.mozac.media

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
}