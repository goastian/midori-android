package org.midorinext.android.ui.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureDataValidationTest {
    @Test
    fun acceptsValidCardNumbersWithOrWithoutSpacing() {
        assertTrue(isValidCardNumber("4111111111111111"))
        assertTrue(isValidCardNumber("4111 1111 1111 1111"))
    }

    @Test
    fun rejectsInvalidOrIncompleteCardNumbers() {
        assertFalse(isValidCardNumber("4111111111111112"))
        assertFalse(isValidCardNumber("1234"))
        assertFalse(isValidCardNumber(""))
    }
}
