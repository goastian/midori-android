package org.midorinext.android.usecases

import org.junit.Assert.assertEquals
import org.junit.Test

class MidoriUseCasesTest {
    @Test
    fun searchQueryValuesArePercentEncoded() {
        assertEquals(
            "C%23%20rock%20%26%20roll%20100%25",
            MidoriUseCases.encodeQueryValue("C# rock & roll 100%"),
        )
        assertEquals(
            "espa%C3%B1ol%20%2B%20privacy",
            MidoriUseCases.encodeQueryValue("español + privacy"),
        )
    }
}
