/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.midorinext.android.R
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.tor.TorForegroundService
import org.midorinext.android.tor.TorIntegration

class TorSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.tor_preferences, rootKey)

        val context = requireContext()

        val torEnabledKey = context.getPreferenceKey(R.string.pref_key_tor_enabled)
        val newIdentityKey = context.getPreferenceKey(R.string.pref_key_tor_new_identity)

        findPreference<SwitchPreferenceCompat>(torEnabledKey)?.apply {
            isChecked = TorIntegration.isTorActive
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                if (enabled) {
                    TorForegroundService.startTor(context)
                } else {
                    TorForegroundService.stopTor(context)
                }
                true
            }
        }

        findPreference<Preference>(newIdentityKey)?.apply {
            isEnabled = TorIntegration.isTorActive
            setOnPreferenceClickListener {
                val service = TorForegroundService.instance
                service?.torManager?.requestNewIdentity()
                Toast.makeText(context, R.string.tor_new_identity_done, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val context = requireContext()
        val torEnabledKey = context.getPreferenceKey(R.string.pref_key_tor_enabled)
        val newIdentityKey = context.getPreferenceKey(R.string.pref_key_tor_new_identity)

        findPreference<SwitchPreferenceCompat>(torEnabledKey)?.isChecked = TorIntegration.isTorActive
        findPreference<Preference>(newIdentityKey)?.isEnabled = TorIntegration.isTorActive
    }
}
