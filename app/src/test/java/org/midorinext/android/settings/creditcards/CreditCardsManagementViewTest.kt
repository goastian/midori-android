/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.creditcards

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import io.mockk.mockk
import mozilla.components.concept.storage.CreditCard
import mozilla.components.support.test.robolectric.testContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.R
import org.midorinext.android.databinding.ComponentCreditCardsBinding
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.settings.autofill.AutofillFragmentState
import org.midorinext.android.settings.creditcards.interactor.CreditCardsManagementInteractor
import org.midorinext.android.settings.creditcards.view.CreditCardsManagementView

@RunWith(MidoriRobolectricTestRunner::class)
class CreditCardsManagementViewTest {

    private lateinit var view: ViewGroup
    private lateinit var interactor: CreditCardsManagementInteractor
    private lateinit var creditCardsView: CreditCardsManagementView
    private lateinit var componentCreditCardsBinding: ComponentCreditCardsBinding

    @Before
    fun setup() {
        view = LayoutInflater.from(testContext).inflate(CreditCardsManagementView.LAYOUT_ID, null)
            .findViewById(R.id.credit_cards_wrapper)
        componentCreditCardsBinding = ComponentCreditCardsBinding.bind(view)
        interactor = mockk(relaxed = true)

        creditCardsView = CreditCardsManagementView(componentCreditCardsBinding, interactor)
    }

    @Test
    fun testUpdate() {
        creditCardsView.update(AutofillFragmentState())

        assertTrue(componentCreditCardsBinding.progressBar.isVisible)
        assertFalse(componentCreditCardsBinding.creditCardsList.isVisible)

        val creditCards: List<CreditCard> = listOf(mockk(), mockk())
        creditCardsView.update(
            AutofillFragmentState(
                creditCards = creditCards,
                isLoading = false
            )
        )

        assertFalse(componentCreditCardsBinding.progressBar.isVisible)
        assertTrue(componentCreditCardsBinding.creditCardsList.isVisible)
    }
}
