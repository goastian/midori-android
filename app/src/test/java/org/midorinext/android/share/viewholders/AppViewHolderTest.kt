/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.share.viewholders

import android.view.LayoutInflater
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.R
import org.midorinext.android.databinding.AppShareListItemBinding
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.share.ShareToAppsInteractor
import org.midorinext.android.share.listadapters.AppShareOption

@RunWith(MidoriRobolectricTestRunner::class)
class AppViewHolderTest {

    private lateinit var binding: AppShareListItemBinding
    private lateinit var viewHolder: AppViewHolder
    private lateinit var interactor: ShareToAppsInteractor

    @Before
    fun setup() {
        interactor = mockk(relaxUnitFun = true)

        binding = AppShareListItemBinding.inflate(LayoutInflater.from(testContext))
        viewHolder = AppViewHolder(binding.root, interactor)
    }

    @Test
    fun `bind app share option`() {
        val app = AppShareOption(
            name = "Wikipedia",
            icon = getDrawable(testContext, R.drawable.ic_fx_accounts_avatar)!!,
            packageName = "org.wikipedia",
            activityName = "MainActivity"
        )
        viewHolder.bind(app)

        assertEquals("Wikipedia", binding.appName.text)
        assertEquals(app.icon, binding.appIcon.drawable)
    }

    @Test
    fun `trigger interactor if application is bound`() {
        val app = AppShareOption(
            name = "Wikipedia",
            icon = getDrawable(testContext, R.drawable.ic_fx_accounts_avatar)!!,
            packageName = "org.wikipedia",
            activityName = "MainActivity"
        )

        viewHolder.itemView.performClick()
        verify { interactor wasNot Called }

        viewHolder.bind(app)
        viewHolder.itemView.performClick()
        verify { interactor.onShareToApp(app) }
    }
}
