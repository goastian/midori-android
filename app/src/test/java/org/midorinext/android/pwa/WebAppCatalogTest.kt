package org.midorinext.android.pwa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class WebAppCatalogTest {
    @Test
    fun suggestionsHaveUniqueSecureAddresses() {
        val suggestions = WebAppCatalog.suggestions
        assertEquals(suggestions.size, suggestions.map { it.url }.toSet().size)
        suggestions.forEach { suggestion ->
            val address = URI(suggestion.url)
            assertEquals("https", address.scheme)
            assertTrue(address.host?.isNotBlank() == true)
            assertTrue(suggestion.name.isNotBlank())
        }
    }
}
