package org.midorinext.android.storage.autofill

import mozilla.components.concept.storage.CreditCardEntry

internal class CreditCardConfirmation(
    val confirm: (CreditCardEntry, String) -> Unit,
) : (CreditCardEntry) -> Unit {
    override fun invoke(card: CreditCardEntry) = confirm(card, "")
}

internal fun isValidSecurityCode(number: String, code: String): Boolean {
    val length = if (number.startsWith("34") || number.startsWith("37")) 4 else 3
    return code.length == length && code.all { it in '0'..'9' }
}
