/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.home.topsites

import android.view.LayoutInflater
import androidx.lifecycle.LifecycleOwner
import io.mockk.mockk
import mozilla.components.feature.top.sites.TopSite
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.databinding.ComponentTopSitesBinding
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.home.sessioncontrol.TopSiteInteractor

@RunWith(MidoriRobolectricTestRunner::class)
class TopSiteViewHolderTest {

    private lateinit var binding: ComponentTopSitesBinding
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var interactor: TopSiteInteractor

    @Before
    fun setup() {
        binding = ComponentTopSitesBinding.inflate(LayoutInflater.from(testContext))
        interactor = mockk(relaxed = true)
        lifecycleOwner = mockk(relaxed = true)
    }

    @Test
    fun `binds list of top sites`() {
        TopSiteViewHolder(binding.root, lifecycleOwner, interactor).bind(
            listOf(
                TopSite.Default(
                    id = 1L,
                    title = "Baidu",
                    url = "https://www.baidu.com/",
                    createdAt = 0
                )
            )
        )

        assertEquals(1, binding.topSitesList.adapter!!.itemCount)
    }
}
