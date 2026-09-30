package org.midorinext.android.usecases

import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.preferences.app.SearchEnginePreference

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

    @Test
    fun selectedSearchEngineBuildsAnEncodedSearchUrl() {
        val query = "café & privacy"
        val encoded = "caf%C3%A9%20%26%20privacy"
        assertEquals(
            "https://www.ecosia.org/search?q=$encoded",
            SearchEngines.url(SearchEnginePreference.ECOSIA, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://www.qwant.com/?q=$encoded",
            SearchEngines.url(SearchEnginePreference.QWANT, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://www.startpage.com/sp/search?query=$encoded",
            SearchEngines.url(SearchEnginePreference.STARTPAGE, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://astiango.com",
            SearchEngines.url(SearchEnginePreference.ASTIANGO, query) { "https://astiango.com" },
        )
    }
}
