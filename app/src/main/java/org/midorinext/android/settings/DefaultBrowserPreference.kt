/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.switchmaterial.SwitchMaterial
import org.midorinext.android.R
import org.midorinext.android.utils.BrowsersCache

class DefaultBrowserPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    private var switchView: SwitchMaterial? = null

    init {
        widgetLayoutResource = R.layout.preference_default_browser
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        switchView = holder.findViewById(R.id.switch_widget) as SwitchMaterial

        updateSwitch()
    }

    fun updateSwitch() {
        val browsers = BrowsersCache.all(context)
        switchView?.isChecked = browsers.isDefaultBrowser
    }
}
