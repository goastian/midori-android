package org.midorinext.android.intent

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.midorinext.android.MainActivity
import org.midorinext.android.usecases.MidoriUseCases
import dagger.hilt.android.AndroidEntryPoint
import mozilla.components.feature.tabs.TabsUseCases
import javax.inject.Inject

@AndroidEntryPoint
class IntentReceiverActivity: AppCompatActivity() {
    @Inject lateinit var tabsUseCases: TabsUseCases
    @Inject lateinit var MidoriUseCases: MidoriUseCases

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent?.let { Intent(it) } ?: Intent()

        val processor = MidoriIntentProcessor(tabsUseCases, MidoriUseCases)
        processor.process(intent)

        intent.setClassName(applicationContext, MainActivity::class.java.name)
        startActivity(intent)
        finish()
    }
}