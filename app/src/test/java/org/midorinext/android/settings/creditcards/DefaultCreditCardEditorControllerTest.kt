/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.creditcards

import android.content.DialogInterface
import androidx.navigation.NavController
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import mozilla.components.concept.storage.CreditCardNumber
import mozilla.components.concept.storage.NewCreditCardFields
import mozilla.components.concept.storage.UpdatableCreditCardFields
import mozilla.components.service.sync.autofill.AutofillCreditCardsAddressesStorage
import mozilla.components.support.test.rule.MainCoroutineRule
import mozilla.components.support.test.rule.runTestOnMain
import mozilla.components.support.utils.CreditCardNetworkType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.midorinext.android.helpers.MidoriRobolectricTestRunner
import org.midorinext.android.settings.creditcards.controller.DefaultCreditCardEditorController

@RunWith(MidoriRobolectricTestRunner::class)
class DefaultCreditCardEditorControllerTest {

    private val storage: AutofillCreditCardsAddressesStorage = mockk(relaxed = true)
    private val navController: NavController = mockk(relaxed = true)
    private val showDeleteDialog = mockk<(DialogInterface.OnClickListener) -> Unit>()

    private lateinit var controller: DefaultCreditCardEditorController

    @get:Rule
    val coroutinesTestRule = MainCoroutineRule()
    private val testDispatcher = coroutinesTestRule.testDispatcher
    private val testCoroutineScope = coroutinesTestRule.scope

    @Before
    fun setup() {
        every { showDeleteDialog(any()) } answers {
            firstArg<DialogInterface.OnClickListener>().onClick(
                mockk(relaxed = true),
                mockk(relaxed = true)
            )
        }
        controller = spyk(
            DefaultCreditCardEditorController(
                storage = storage,
                lifecycleScope = testCoroutineScope,
                navController = navController,
                ioDispatcher = testDispatcher,
                showDeleteDialog = showDeleteDialog
            )
        )
    }

    @Test
    fun handleCancelButtonClicked() {
        controller.handleCancelButtonClicked()

        verify {
            navController.popBackStack()
        }
    }

    @Test
    fun handleDeleteCreditCard() = runTestOnMain {
        val creditCardId = "id"

        controller.handleDeleteCreditCard(creditCardId)

        coVerify {
            storage.deleteCreditCard(creditCardId)
            navController.popBackStack()
        }
    }

    @Test
    fun handleSaveCreditCard() = runTestOnMain {
        val creditCardFields = NewCreditCardFields(
            billingName = "Banana Apple",
            plaintextCardNumber = CreditCardNumber.Plaintext("4111111111111112"),
            cardNumberLast4 = "1112",
            expiryMonth = 1,
            expiryYear = 2030,
            cardType = CreditCardNetworkType.DISCOVER.cardName
        )

        controller.handleSaveCreditCard(creditCardFields)

        coVerify {
            storage.addCreditCard(creditCardFields)
            navController.popBackStack()
        }
    }

    @Test
    fun handleUpdateCreditCard() = runTestOnMain {
        val creditCardId = "id"
        val creditCardFields = UpdatableCreditCardFields(
            billingName = "Banana Apple",
            cardNumber = CreditCardNumber.Plaintext("4111111111111112"),
            cardNumberLast4 = "1112",
            expiryMonth = 1,
            expiryYear = 2034,
            cardType = CreditCardNetworkType.DISCOVER.cardName
        )

        controller.handleUpdateCreditCard(creditCardId, creditCardFields)

        coVerify {
            storage.updateCreditCard(creditCardId, creditCardFields)
            navController.popBackStack()
        }
    }
}
