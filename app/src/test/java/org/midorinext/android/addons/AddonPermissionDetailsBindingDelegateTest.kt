/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.addons

import android.view.LayoutInflater
import android.view.View
import androidx.core.net.toUri
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.feature.addons.Addon
import mozilla.components.support.test.robolectric.testContext
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.databinding.FragmentAddOnPermissionsBinding
import org.midorinext.android.helpers.MidoriRobolectricTestRunner

@RunWith(MidoriRobolectricTestRunner::class)
class AddonPermissionDetailsBindingDelegateTest {

    private lateinit var view: View
    private lateinit var binding: FragmentAddOnPermissionsBinding
    private lateinit var interactor: AddonPermissionsDetailsInteractor
    private lateinit var permissionDetailsBindingDelegate: AddonPermissionDetailsBindingDelegate
    private val addon = Addon(
        id = "",
        translatableName = mapOf(
            Addon.DEFAULT_LOCALE to "Some blank addon"
        )
    )
    private val learnMoreUrl =
        "https://support.mozilla.org/kb/permission-request-messages-firefox-extensions"

    @Before
    fun setup() {
        binding = FragmentAddOnPermissionsBinding.inflate(LayoutInflater.from(testContext))
        view = binding.root
        interactor = mockk(relaxed = true)
        permissionDetailsBindingDelegate = AddonPermissionDetailsBindingDelegate(binding, interactor)
    }

    @Test
    fun `clicking learn more opens learn more page in browser`() {
        permissionDetailsBindingDelegate.bind(
            addon.copy(
                rating = null
            )
        )

        permissionDetailsBindingDelegate.binding.learnMoreLabel.performClick()

        verify { interactor.openWebsite(learnMoreUrl.toUri()) }
    }
}
