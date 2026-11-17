package org.midorinext.android

import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mozilla.components.browser.state.action.AppLifecycleAction
import mozilla.components.feature.pwa.intent.WebAppIntentProcessor
import mozilla.components.lib.state.ext.flow
import mozilla.components.support.base.android.NotificationsDelegate
import org.midorinext.android.intent.MidoriIntentProcessor
import org.midorinext.android.mozac.media.BackgroundPlaybackFeature
import org.midorinext.android.pwa.WebAppActivity
import org.midorinext.android.ui.MidoriBrowserApp
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : MidoriActivity() {
    private val showBrowser = MutableStateFlow(false)
    private val pendingBrowserRequests = MutableStateFlow(0)
    private var browserIntentConsumed = false
    private var intentGeneration = 0L
    private val intentMutex = Mutex()
    @Inject lateinit var notificationsDelegate: NotificationsDelegate
    @Inject lateinit var backgroundPlaybackFeature: BackgroundPlaybackFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MidoriApplication).initializeBrowser("main_activity")
        super.onCreate(savedInstanceState)

        notificationsDelegate.bindToActivity(this)
        browserIntentConsumed = savedInstanceState?.getBoolean("browser_intent_consumed", true) == true
        showBrowser.value = savedInstanceState?.getBoolean("show_browser") == true
        if (!browserIntentConsumed &&
            (savedInstanceState != null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0)
        ) {
            handleBrowserIntent(intent)
        }

        val v = ComposeView(this).apply {
            setContent {
                MidoriBrowserApp(
                    showBrowser = showBrowser,
                    pendingBrowserRequests = pendingBrowserRequests,
                    onBrowserShown = { showBrowser.value = false },
                )
            }
        }
        setContentView(v)
        this.bindRootView(v.rootView)
        v.doOnPreDraw {
            // Post from pre-draw so deferred work starts after the first traversal.
            v.post { (application as MidoriApplication).onFirstFrameDrawn() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        browserIntentConsumed = false
        handleBrowserIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("browser_intent_consumed", browserIntentConsumed)
        outState.putBoolean("show_browser", showBrowser.value)
        super.onSaveInstanceState(outState)
    }

    private fun handleBrowserIntent(incoming: Intent) {
        val request = Intent(incoming)
        val generation = ++intentGeneration
        pendingBrowserRequests.value += 1
        lifecycleScope.launch {
            try {
                intentMutex.withLock {
                    val app = application as MidoriApplication
                    app.store.get().flow().first { it.restoreComplete }
                    if (request.action == WebAppIntentProcessor.ACTION_VIEW_PWA && request.dataString != null) {
                        startActivity(Intent(this@MainActivity, WebAppActivity::class.java).apply {
                            action = Intent.ACTION_VIEW
                            data = request.data
                            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        })
                    } else {
                        val processor = MidoriIntentProcessor(app.tabsUseCases.get(), app.MidoriUseCases.get(), app.store.get())
                        if (processor.process(request)) showBrowser.value = true
                    }
                    if (generation == intentGeneration) {
                        browserIntentConsumed = true
                        setIntent(Intent(this@MainActivity, MainActivity::class.java).apply {
                            action = Intent.ACTION_MAIN
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                    }
                }
            } finally {
                pendingBrowserRequests.value -= 1
            }
        }
    }

    override fun onPause() {
        backgroundPlaybackFeature.onBrowserActivityPaused()
        (application as MidoriApplication).store.get().dispatch(AppLifecycleAction.PauseAction)
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            backgroundPlaybackFeature.onBrowserActivityLosingFocus()
        }
        super.onWindowFocusChanged(hasFocus)
    }

    override fun onResume() {
        super.onResume()
        backgroundPlaybackFeature.onBrowserActivityResumed()
        (application as MidoriApplication).store.get().dispatch(AppLifecycleAction.ResumeAction)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as MidoriApplication).onBrowserClosed()
        }
    }
}
