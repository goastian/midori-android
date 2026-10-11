package org.midorinext.android.newtab

import org.midorinext.android.preferences.app.AppPreferences

internal fun newTabUrl(preferences: AppPreferences, private: Boolean): String = when {
    private -> NATIVE_HOME_URL
    preferences.openBlankNewTab -> "about:blank"
    else -> NATIVE_HOME_URL
}
