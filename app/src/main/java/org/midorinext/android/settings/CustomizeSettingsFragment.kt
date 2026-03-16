/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.ListPreference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import org.midorinext.android.R
import org.midorinext.android.ext.getPreferenceKey

class CustomizeSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.customize_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()

        (activity as? SettingsFragment.ActionBarUpdater)?.updateTitle(R.string.customize_category)

        val toolbarPositionKey = requireContext().getPreferenceKey(R.string.pref_key_toolbar_position)
        val prefToolbarPosition = findPreference<ListPreference>(toolbarPositionKey)

        prefToolbarPosition?.let { pref ->
            updateToolbarPositionSummary(pref)
            pref.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
                updateToolbarPositionSummary(preference as ListPreference, newValue as String)
                true
            }
        }

        val themeKey = requireContext().getPreferenceKey(R.string.pref_key_theme)
        val prefTheme = findPreference<ListPreference>(themeKey)

        prefTheme?.let { pref ->
            updateThemeSummary(pref)
            pref.onPreferenceChangeListener = OnPreferenceChangeListener { preference, newValue ->
                val themeValue = newValue as String
                updateThemeSummary(preference as ListPreference, themeValue)
                applyTheme(themeValue)
                true
            }
        }
    }

    private fun updateToolbarPositionSummary(pref: ListPreference, value: String? = null) {
        val current = value ?: pref.value ?: "bottom"
        pref.summary = if (current == "top") {
            getString(R.string.preferences_toolbar_position_summary_top)
        } else {
            getString(R.string.preferences_toolbar_position_summary_bottom)
        }
    }

    private fun updateThemeSummary(pref: ListPreference, value: String? = null) {
        val current = value ?: pref.value ?: "system"
        pref.summary = when (current) {
            "light" -> getString(R.string.preferences_theme_summary_light)
            "dark" -> getString(R.string.preferences_theme_summary_dark)
            else -> getString(R.string.preferences_theme_summary_system)
        }
    }

    companion object {
        fun applyTheme(themeValue: String) {
            when (themeValue) {
                "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                "dark" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }
}
