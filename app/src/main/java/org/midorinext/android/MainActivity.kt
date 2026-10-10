package org.midorinext.android

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import org.midorinext.android.mozac.media.BackgroundPlaybackFeature
import org.midorinext.android.ui.MidoriBrowserApp
import dagger.hilt.android.AndroidEntryPoint
import mozilla.components.support.base.android.NotificationsDelegate
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : MidoriActivity() {
    @Inject lateinit var notificationsDelegate: NotificationsDelegate
    @Inject lateinit var backgroundPlaybackFeature: BackgroundPlaybackFeature

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationsDelegate.bindToActivity(this)

        val v = ComposeView(this).apply {
            setContent {
                MidoriBrowserApp()
            }
        }
        setContentView(v)
        this.bindRootView(v.rootView)
        v.doOnPreDraw {
            // Post from pre-draw so deferred work starts after the first traversal.
            v.post { (application as MidoriApplication).onFirstFrameDrawn() }
        }
    }

    override fun onPause() {
        backgroundPlaybackFeature.onBrowserActivityPaused()
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
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            (application as MidoriApplication).onBrowserClosed()
        }
    }
}
