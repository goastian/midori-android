/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.creditcards

import io.mockk.mockk
import io.mockk.verify
import mozilla.components.concept.storage.CreditCard
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.settings.creditcards.controller.CreditCardsManagementController
import org.midorinext.android.settings.creditcards.interactor.DefaultCreditCardsManagementInteractor

@RunWith(MidoriRobolectricTestRunner::class)
class DefaultCreditCardsManagementInteractorTest {

    private val controller: CreditCardsManagementController = mockk(relaxed = true)

    private lateinit var interactor: DefaultCreditCardsManagementInteractor

    @Before
    fun setup() {
        interactor = DefaultCreditCardsManagementInteractor(controller)
    }

    @Test
    fun onSelectCreditCard() {
        val creditCard: CreditCard = mockk(relaxed = true)

        interactor.onSelectCreditCard(creditCard)
        verify { controller.handleCreditCardClicked(creditCard) }
    }

    @Test
    fun onClickAddCreditCard() {
        interactor.onAddCreditCardClick()
        verify { controller.handleAddCreditCardClicked() }
    }
}
