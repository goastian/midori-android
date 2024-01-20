/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.sitepermissions

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.permission.SitePermissions
import mozilla.components.support.ktx.kotlin.stripDefaultPort
import org.midorinext.android.R
import org.midorinext.android.ext.components
import org.midorinext.android.ext.requireComponents
import org.midorinext.android.ext.settings
import org.midorinext.android.ext.showToolbar
import org.midorinext.android.settings.PhoneFeature
import org.midorinext.android.settings.PhoneFeature.CAMERA
import org.midorinext.android.settings.PhoneFeature.LOCATION
import org.midorinext.android.settings.PhoneFeature.MICROPHONE
import org.midorinext.android.settings.PhoneFeature.NOTIFICATION
import org.midorinext.android.settings.PhoneFeature.PERSISTENT_STORAGE
import org.midorinext.android.settings.PhoneFeature.MEDIA_KEY_SYSTEM_ACCESS
import org.midorinext.android.settings.PhoneFeature.AUTOPLAY
import org.midorinext.android.settings.PhoneFeature.CROSS_ORIGIN_STORAGE_ACCESS
import org.midorinext.android.settings.quicksettings.AutoplayValue
import org.midorinext.android.settings.requirePreference
import org.midorinext.android.utils.Settings

@SuppressWarnings("TooManyFunctions")
class SitePermissionsDetailsExceptionsFragment : PreferenceFragmentCompat() {
    @VisibleForTesting
    internal lateinit var sitePermissions: SitePermissions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sitePermissions = SitePermissionsDetailsExceptionsFragmentArgs
            .fromBundle(requireArguments())
            .sitePermissions
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.site_permissions_details_exceptions_preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        showToolbar(sitePermissions.origin.stripDefaultPort())
        viewLifecycleOwner.lifecycleScope.launch(Main) {
            sitePermissions =
                requireNotNull(
                    requireComponents.core.permissionStorage.findSitePermissionsBy(
                        sitePermissions.origin,
                        private = false,
                    ),
                )
            bindCategoryPhoneFeatures()
        }
    }

    @VisibleForTesting
    internal fun bindCategoryPhoneFeatures() {
        initPhoneFeature(CAMERA)
        initPhoneFeature(LOCATION)
        initPhoneFeature(MICROPHONE)
        initPhoneFeature(NOTIFICATION)
        initPhoneFeature(PERSISTENT_STORAGE)
        initPhoneFeature(CROSS_ORIGIN_STORAGE_ACCESS)
        initPhoneFeature(MEDIA_KEY_SYSTEM_ACCESS)
        initAutoplayFeature()
        bindClearPermissionsButton()
    }

    @VisibleForTesting
    internal fun initPhoneFeature(phoneFeature: PhoneFeature) {
        val summary = phoneFeature.getActionLabel(provideContext(), sitePermissions)
        val cameraPhoneFeatures = getPreference(phoneFeature)
        cameraPhoneFeatures.summary = summary

        cameraPhoneFeatures.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            navigateToPhoneFeature(phoneFeature)
            true
        }
    }

    @VisibleForTesting
    internal fun getPreference(phoneFeature: PhoneFeature): Preference =
        requirePreference(phoneFeature.getPreferenceId())

    @VisibleForTesting
    internal fun provideContext(): Context = requireContext()

    @VisibleForTesting
    internal fun provideSettings(): Settings = provideContext().settings()

    @VisibleForTesting
    internal fun initAutoplayFeature() {
        val phoneFeature = getPreference(AUTOPLAY)
        phoneFeature.summary = getAutoplayLabel()

        phoneFeature.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            navigateToPhoneFeature(AUTOPLAY)
            true
        }
    }

    @VisibleForTesting
    internal fun getAutoplayLabel(): String {
        val context = provideContext()
        val settings = provideSettings()
        val autoplayValues = AutoplayValue.values(context, settings, sitePermissions)
        val selected =
            autoplayValues.firstOrNull { it.isSelected() } ?: AutoplayValue.getFallbackValue(
                context,
                settings,
                sitePermissions
            )

        return selected.label
    }

    @VisibleForTesting
    internal fun bindClearPermissionsButton() {
        val button: Preference = requirePreference(R.string.pref_key_exceptions_clear_site_permissions)

        button.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(requireContext()).apply {
                setMessage(R.string.confirm_clear_permissions_site)
                setTitle(R.string.clear_permissions)
                setPositiveButton(R.string.clear_permissions_positive) { dialog: DialogInterface, _ ->
                    clearSitePermissions()
                    dialog.dismiss()
                }
                setNegativeButton(R.string.clear_permissions_negative) { dialog: DialogInterface, _ ->
                    dialog.cancel()
                }
            }.show()

            true
        }
    }

    private fun clearSitePermissions() {
        // Use fragment's lifecycle; the view may be gone by the time dialog is interacted with.
        lifecycleScope.launch(IO) {
            requireContext().components.core.permissionStorage.deleteSitePermissions(sitePermissions)
            withContext(Main) {
                requireView().findNavController().popBackStack()
                requireContext().components.tryReloadTabBy(sitePermissions.origin)
            }
        }
    }

    @VisibleForTesting
    internal fun navigateToPhoneFeature(phoneFeature: PhoneFeature) {
        val directions =
            SitePermissionsDetailsExceptionsFragmentDirections.actionSitePermissionsToExceptionsToManagePhoneFeature(
                phoneFeature = phoneFeature,
                sitePermissions = sitePermissions
            )
        requireView().findNavController().navigate(directions)
    }
}
