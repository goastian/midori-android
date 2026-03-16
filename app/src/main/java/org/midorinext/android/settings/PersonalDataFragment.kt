/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.midorinext.android.R
import org.midorinext.android.autofill.AutofillPreference
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.settings.personaldata.PasswordsFragment
import org.midorinext.android.settings.personaldata.CardsFragment
import org.midorinext.android.settings.personaldata.AddressesFragment
import org.midorinext.android.settings.personaldata.ContactInfoFragment

class PersonalDataFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.personal_data_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        updateAutofillPreference()
        updatePasswordCount()
        updateCardCount()
        updateAddressCount()
        updateContactCount()
        setupClickListeners()
    }

    private fun updateAutofillPreference() {
        val autofillKey = requireContext().getPreferenceKey(R.string.pref_key_autofill)
        val autofillPref = findPreference<Preference>(autofillKey)
        if (autofillPref != null) {
            if (!AutofillPreference.isSupported(requireContext())) {
                autofillPref.isVisible = false
            } else {
                (autofillPref as? AutofillPreference)?.updateSwitch()
            }
        }
    }

    private fun setupClickListeners() {
        val passwordsKey = requireContext().getPreferenceKey(R.string.pref_key_passwords)
        findPreference<Preference>(passwordsKey)?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right,
                )
                .replace(R.id.container, PasswordsFragment())
                .addToBackStack(null)
                .commit()
            (activity as? SettingsFragment.ActionBarUpdater)?.updateTitle(R.string.passwords_title)
            true
        }

        val cardsKey = requireContext().getPreferenceKey(R.string.pref_key_saved_cards)
        findPreference<Preference>(cardsKey)?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right,
                )
                .replace(R.id.container, CardsFragment())
                .addToBackStack(null)
                .commit()
            (activity as? SettingsFragment.ActionBarUpdater)?.updateTitle(R.string.saved_cards_title)
            true
        }

        val addressesKey = requireContext().getPreferenceKey(R.string.pref_key_saved_addresses)
        findPreference<Preference>(addressesKey)?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right,
                )
                .replace(R.id.container, AddressesFragment())
                .addToBackStack(null)
                .commit()
            (activity as? SettingsFragment.ActionBarUpdater)?.updateTitle(R.string.saved_addresses_title)
            true
        }

        val contactKey = requireContext().getPreferenceKey(R.string.pref_key_contact_info)
        findPreference<Preference>(contactKey)?.setOnPreferenceClickListener {
            parentFragmentManager
                .beginTransaction()
                .setCustomAnimations(
                    R.anim.slide_in_right, R.anim.slide_out_left,
                    R.anim.slide_in_left, R.anim.slide_out_right,
                )
                .replace(R.id.container, ContactInfoFragment())
                .addToBackStack(null)
                .commit()
            (activity as? SettingsFragment.ActionBarUpdater)?.updateTitle(R.string.contact_info_title)
            true
        }
    }

    private fun updatePasswordCount() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val storage = requireComponents.core.loginsStorage
                val logins = storage.list()
                val count = logins.size
                withContext(Dispatchers.Main) {
                    val key = requireContext().getPreferenceKey(R.string.pref_key_passwords)
                    findPreference<Preference>(key)?.summary = if (count > 0) {
                        getString(R.string.passwords_summary, count)
                    } else {
                        getString(R.string.passwords_empty)
                    }
                }
            } catch (_: Exception) {
                // Storage not ready yet
            }
        }
    }

    private fun updateCardCount() {
        val prefs = requireContext().getSharedPreferences("midori_cards", 0)
        val count = prefs.getInt("card_count", 0)
        val key = requireContext().getPreferenceKey(R.string.pref_key_saved_cards)
        findPreference<Preference>(key)?.summary = if (count > 0) {
            getString(R.string.saved_cards_summary, count)
        } else {
            getString(R.string.saved_cards_empty)
        }
    }

    private fun updateAddressCount() {
        val prefs = requireContext().getSharedPreferences("midori_addresses", 0)
        val count = prefs.getInt("address_count", 0)
        val key = requireContext().getPreferenceKey(R.string.pref_key_saved_addresses)
        findPreference<Preference>(key)?.summary = if (count > 0) {
            getString(R.string.saved_addresses_summary, count)
        } else {
            getString(R.string.saved_addresses_empty)
        }
    }

    private fun updateContactCount() {
        val prefs = requireContext().getSharedPreferences("midori_contacts", 0)
        val count = prefs.getInt("contact_count", 0)
        val key = requireContext().getPreferenceKey(R.string.pref_key_contact_info)
        findPreference<Preference>(key)?.summary = if (count > 0) {
            getString(R.string.contact_info_summary, count)
        } else {
            getString(R.string.contact_info_empty)
        }
    }
}
