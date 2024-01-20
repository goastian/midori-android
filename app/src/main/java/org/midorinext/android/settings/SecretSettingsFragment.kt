/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import org.midorinext.android.R
import org.midorinext.android.ext.components
import org.midorinext.android.ext.settings
import org.midorinext.android.ext.showToolbar

class SecretSettingsFragment : Fragment() {

    lateinit var view: SecretSettingsComposeView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        view = SecretSettingsComposeView(requireContext())
        return view
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_debug_settings))
        setupPreferences()
    }

    private fun setupPreferences() {
        view.onAllowThirdPartyRootCertsChange = { allow ->
            requireContext().components.core.engine.settings.enterpriseRootsEnabled = allow
        }
    }

}
