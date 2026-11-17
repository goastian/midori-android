package org.midorinext.android.intent

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.midorinext.android.MainActivity
import org.midorinext.android.MidoriApplication
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IntentReceiverActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        (application as MidoriApplication).initializeBrowser("intent_receiver")
        super.onCreate(savedInstanceState)

        val intent = intent?.let { Intent(it) } ?: Intent()

        intent.setClassName(applicationContext, MainActivity::class.java.name)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
