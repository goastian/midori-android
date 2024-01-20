/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.deletebrowsingdata

import android.content.Context
import androidx.annotation.StringRes
import org.midorinext.android.R
import org.midorinext.android.ext.getPreferenceKey

enum class DeleteBrowsingDataOnQuitType(@StringRes private val prefKey: Int) {
    TABS(R.string.pref_key_delete_open_tabs_on_quit),
    HISTORY(R.string.pref_key_delete_browsing_history_on_quit),
    COOKIES(R.string.pref_key_delete_cookies_on_quit),
    CACHE(R.string.pref_key_delete_caches_on_quit),
    PERMISSIONS(R.string.pref_key_delete_permissions_on_quit),
    DOWNLOADS(R.string.pref_key_delete_downloads_on_quit);

    fun getPreferenceKey(context: Context) = context.getPreferenceKey(prefKey)
}
