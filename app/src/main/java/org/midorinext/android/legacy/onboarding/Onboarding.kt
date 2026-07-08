package org.midorinext.android.legacy.onboarding

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.preference.PreferenceManager
import org.midorinext.android.R

@Composable
fun Onboarding(
    onOnboardingEnd: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefkey = stringResource(id = R.string.pref_key_show_onboarding)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(prefkey, false)
                .apply()
            onOnboardingEnd(true)
        } else {
            onOnboardingEnd(false)
        }
    }

    // TODO move shouldShowOnboarding from SharedPrefs to datastore
    //  and responsibility to viewmodel
    LaunchedEffect(true) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val shouldShowOnboarding = prefs.getBoolean(prefkey, true)
        if (shouldShowOnboarding) {
            launcher.launch(
                Intent(context, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
        }
    }
}