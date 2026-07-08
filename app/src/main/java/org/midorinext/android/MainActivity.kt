package org.midorinext.android

import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.midorinext.android.storage.MidoriClientProvider
import org.midorinext.android.ui.MidoriBrowserApp
import dagger.hilt.android.AndroidEntryPoint
import mozilla.components.support.base.android.NotificationsDelegate
import javax.inject.Inject
import kotlin.system.exitProcess

@AndroidEntryPoint
class MainActivity : MidoriActivity() {
    @Inject lateinit var notificationsDelegate: NotificationsDelegate
    @Inject lateinit var clientProvider: MidoriClientProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clientProvider.bindToActivity(this)
        notificationsDelegate.bindToActivity(this)

        val v = ComposeView(this).apply {
            setContent {
                MidoriBrowserApp()
            }
        }
        setContentView(v)
        this.bindRootView(v.rootView)
    }
}
