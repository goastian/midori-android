package org.midorinext.android.companion

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setResult(RESULT_OK, Intent().putExtra("partner_client", BuildConfig.PARTNER_CLIENT))
        this.finish()
    }
}