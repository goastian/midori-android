package org.midorinext.android.widget

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import org.midorinext.android.MidoriActivity
import org.midorinext.android.intent.IntentReceiverActivity
import org.midorinext.android.ui.theme.MidoriBrowserTheme
import org.midorinext.android.widget.ui.Widget
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WidgetActivity: MidoriActivity() {
    private val viewModel: WidgetViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = ComposeView(this).apply {
            setContent {
                MidoriBrowserTheme {
                    Widget(viewModel)
                }
            }
        }
        setContentView(v)
        this.bindRootView(v.rootView)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.resetSearch()
    }

    fun openFullBrowsingActivity(url: String) {
        startActivity(
            Intent(this, IntentReceiverActivity::class.java).also {
                it.setPackage(packageName)
                it.action = Intent.ACTION_VIEW
                it.data = Uri.parse(url)
            }
        )
    }
}