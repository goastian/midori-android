package org.midorinext.android.storage.autofill

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditCardSecurityCodeTest {
    @Test
    fun cardNetworkDeterminesCodeLengthAndLeadingZerosArePreserved() {
        assertTrue(isValidSecurityCode("4111111111111111", "001"))
        assertTrue(isValidSecurityCode("378282246310005", "0001"))
        assertFalse(isValidSecurityCode("4111111111111111", "0001"))
        assertFalse(isValidSecurityCode("378282246310005", "001"))
    }

    @Test
    fun emptyNonAsciiAndNonNumericCodesAreRejected() {
        for (code in listOf("", "12", "12x", "１２３", "123 ", "12345")) {
            assertFalse(isValidSecurityCode("4111111111111111", code))
        }
    }
}
