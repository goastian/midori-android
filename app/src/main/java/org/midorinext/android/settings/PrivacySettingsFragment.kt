/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import mozilla.components.concept.engine.EngineSession.TrackingProtectionPolicy
import org.midorinext.android.R
import org.midorinext.android.ext.getPreferenceKey
import org.midorinext.android.ext.requireComponents

class PrivacySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.privacy_preferences, rootKey)

        val context = requireContext()

        // Protection Level
        val privacyLevelKey = context.getPreferenceKey(R.string.pref_key_privacy_level)
        val prefPrivacyLevel = findPreference<ListPreference>(privacyLevelKey)
        prefPrivacyLevel?.onPreferenceChangeListener = getChangeListenerForPrivacyLevel()
        updatePrivacyLevelSummary(prefPrivacyLevel)

        // Tracking Protection
        val trackingProtectionNormalKey = context.getPreferenceKey(R.string.pref_key_tracking_protection_normal)
        val trackingProtectionPrivateKey = context.getPreferenceKey(R.string.pref_key_tracking_protection_private)
        val globalPrivacyControlKey = context.getPreferenceKey(R.string.pref_key_global_privacy_control)

        val prefTrackingProtectionNormal = findPreference<SwitchPreferenceCompat>(trackingProtectionNormalKey)
        val prefTrackingProtectionPrivate = findPreference<SwitchPreferenceCompat>(trackingProtectionPrivateKey)
        val globalPrivacyControl = findPreference<SwitchPreferenceCompat>(globalPrivacyControlKey)

        prefTrackingProtectionNormal?.onPreferenceChangeListener = getChangeListenerForTrackingProtection { enabled ->
            requireComponents.core.createTrackingProtectionPolicy(normalMode = enabled)
        }
        prefTrackingProtectionPrivate?.onPreferenceChangeListener = getChangeListenerForTrackingProtection { enabled ->
            requireComponents.core.createTrackingProtectionPolicy(privateMode = enabled)
        }
        globalPrivacyControl?.onPreferenceChangeListener = getChangeListenerForGPC { enabled ->
            requireComponents.core.engine.settings.globalPrivacyControlEnabled = enabled
            requireComponents.useCases.sessionUseCases.reload.invoke()
        }

        // Cookie blocking
        val cookieBlockingKey = context.getPreferenceKey(R.string.pref_key_cookie_blocking)
        findPreference<SwitchPreferenceCompat>(cookieBlockingKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, value ->
                val policy = if (value as Boolean) {
                    TrackingProtectionPolicy.strict()
                } else {
                    requireComponents.core.createTrackingProtectionPolicy()
                }
                requireComponents.useCases.settingsUseCases.updateTrackingProtection.invoke(policy)
                true
            }

        // Fingerprint protection
        val fingerprintKey = context.getPreferenceKey(R.string.pref_key_fingerprint_protection)
        findPreference<SwitchPreferenceCompat>(fingerprintKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }

        // Block redirects
        val blockRedirectsKey = context.getPreferenceKey(R.string.pref_key_block_redirects)
        findPreference<SwitchPreferenceCompat>(blockRedirectsKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }

        // Strip tracking params
        val stripParamsKey = context.getPreferenceKey(R.string.pref_key_strip_tracking_params)
        findPreference<SwitchPreferenceCompat>(stripParamsKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }

        // HTTPS-Only
        val httpsOnlyKey = context.getPreferenceKey(R.string.pref_key_https_only)
        findPreference<SwitchPreferenceCompat>(httpsOnlyKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }

        // DNS over HTTPS
        val dohKey = context.getPreferenceKey(R.string.pref_key_doh)
        findPreference<SwitchPreferenceCompat>(dohKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }

        // Clear on exit
        val clearOnExitKey = context.getPreferenceKey(R.string.pref_key_clear_on_exit)
        findPreference<SwitchPreferenceCompat>(clearOnExitKey)?.onPreferenceChangeListener =
            OnPreferenceChangeListener { _, _ -> true }
    }

    private fun getChangeListenerForPrivacyLevel(): OnPreferenceChangeListener =
        OnPreferenceChangeListener { preference, newValue ->
            val level = newValue as String
            applyPrivacyLevel(level)
            updatePrivacyLevelSummary(preference as? ListPreference)
            true
        }

    private fun applyPrivacyLevel(level: String) {
        val context = requireContext()
        val trackingNormalKey = context.getPreferenceKey(R.string.pref_key_tracking_protection_normal)
        val trackingPrivateKey = context.getPreferenceKey(R.string.pref_key_tracking_protection_private)
        val gpcKey = context.getPreferenceKey(R.string.pref_key_global_privacy_control)
        val cookieKey = context.getPreferenceKey(R.string.pref_key_cookie_blocking)
        val fingerprintKey = context.getPreferenceKey(R.string.pref_key_fingerprint_protection)
        val httpsKey = context.getPreferenceKey(R.string.pref_key_https_only)
        val dohKey = context.getPreferenceKey(R.string.pref_key_doh)

        when (level) {
            "standard" -> {
                findPreference<SwitchPreferenceCompat>(trackingNormalKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(trackingPrivateKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(gpcKey)?.isChecked = false
                findPreference<SwitchPreferenceCompat>(cookieKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(fingerprintKey)?.isChecked = false
                findPreference<SwitchPreferenceCompat>(httpsKey)?.isChecked = false
                findPreference<SwitchPreferenceCompat>(dohKey)?.isChecked = false
            }
            "strict" -> {
                findPreference<SwitchPreferenceCompat>(trackingNormalKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(trackingPrivateKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(gpcKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(cookieKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(fingerprintKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(httpsKey)?.isChecked = true
                findPreference<SwitchPreferenceCompat>(dohKey)?.isChecked = true
                val policy = TrackingProtectionPolicy.strict()
                requireComponents.useCases.settingsUseCases.updateTrackingProtection.invoke(policy)
                requireComponents.core.engine.settings.globalPrivacyControlEnabled = true
            }
        }
        // "custom" = let user toggle each preference manually
    }

    private fun updatePrivacyLevelSummary(pref: ListPreference?) {
        pref ?: return
        pref.summary = when (pref.value) {
            "standard" -> getString(R.string.privacy_level_standard_desc)
            "strict" -> getString(R.string.privacy_level_strict_desc)
            "custom" -> getString(R.string.privacy_level_custom_desc)
            else -> getString(R.string.privacy_level_summary)
        }
    }

    private fun getChangeListenerForTrackingProtection(
        createTrackingProtectionPolicy: (Boolean) -> TrackingProtectionPolicy,
    ): OnPreferenceChangeListener =
        OnPreferenceChangeListener { _, value ->
            val policy = createTrackingProtectionPolicy(value as Boolean)
            requireComponents.useCases.settingsUseCases.updateTrackingProtection.invoke(policy)
            true
        }

    private fun getChangeListenerForGPC(action: (Boolean) -> Unit): OnPreferenceChangeListener =
        OnPreferenceChangeListener { _, enabled ->
            action.invoke(enabled as Boolean)
            true
        }
}
