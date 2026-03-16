/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.components

import mozilla.components.concept.storage.CreditCardEntry
import mozilla.components.concept.storage.CreditCardValidationDelegate
import mozilla.components.concept.storage.CreditCardsAddressesStorage

/**
 * A delegate that will check against [creditCardsStorage] to see if a given credit card
 * can be saved or updated.
 */
class DefaultCreditCardValidationDelegate(
    private val creditCardsStorage: Lazy<CreditCardsAddressesStorage>,
) : CreditCardValidationDelegate {

    override suspend fun shouldCreateOrUpdate(creditCard: CreditCardEntry): CreditCardValidationDelegate.Result {
        val validCreditCards = creditCardsStorage.value.getAllCreditCards()

        for (card in validCreditCards) {
            if (card.cardNumberLast4 == creditCard.number.takeLast(4)) {
                return CreditCardValidationDelegate.Result.CanBeUpdated(card)
            }
        }

        return CreditCardValidationDelegate.Result.CanBeCreated
    }
}
