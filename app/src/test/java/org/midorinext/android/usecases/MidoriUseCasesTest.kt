package org.midorinext.android.usecases

import org.junit.Assert.assertEquals
import org.junit.Test
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.CustomSearchEngine

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
            "https://www.bing.com/search?q=$encoded",
            SearchEngines.url(SearchEnginePreference.BING, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://www.google.com/search?q=$encoded",
            SearchEngines.url(SearchEnginePreference.GOOGLE, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://duckduckgo.com/?q=$encoded",
            SearchEngines.url(SearchEnginePreference.DUCKDUCKGO, query) { "https://astiango.com" },
        )
        assertEquals(
            "https://astiango.com",
            SearchEngines.url(SearchEnginePreference.ASTIANGO, query) { "https://astiango.com" },
        )
    }

    @Test
    fun newSearchEnginesAreAvailableAndPersistForNormalAndPrivateBrowsing() {
        val engines = listOf(
            SearchEnginePreference.BING to "https://www.bing.com/search?q=privacy",
            SearchEnginePreference.GOOGLE to "https://www.google.com/search?q=privacy",
            SearchEnginePreference.DUCKDUCKGO to "https://duckduckgo.com/?q=privacy",
        )
        engines.forEach { (engine, expectedUrl) ->
            val saved = AppPreferences.newBuilder()
                .setSearchEngine(engine)
                .setPrivateSearchEngine(engine)
                .setUseSeparatePrivateSearchEngine(true)
                .build()
            val restored = AppPreferences.parseFrom(saved.toByteArray())
            assertEquals(engine, restored.searchEngine)
            assertEquals(engine, restored.privateSearchEngine)
            val id = SearchEngines.id(engine)
            assertEquals(true, SearchEngines.options(restored).any { it.id == id })
            listOf(false, true).forEach { private ->
                assertEquals(id, SearchEngines.selectedId(restored, private))
                assertEquals(
                    expectedUrl,
                    SearchEngines.url(restored, SearchEngines.selectedId(restored, private), "privacy") {
                        error("The selected search engine should handle the query")
                    },
                )
            }
        }
    }

    @Test
    fun privateSearchEngineUsesNormalEngineUntilSeparatelySelected() {
        val preferences = AppPreferences.newBuilder()
            .setSearchEngine(SearchEnginePreference.ECOSIA)
            .setPrivateSearchEngine(SearchEnginePreference.QWANT)
            .build()
        assertEquals("ecosia", SearchEngines.selectedId(preferences, private = true))
        assertEquals(
            "qwant",
            SearchEngines.selectedId(
                preferences.toBuilder().setUseSeparatePrivateSearchEngine(true).build(),
                private = true,
            ),
        )
    }

    @Test
    fun customEngineRequiresHttpsAndEncodesQuery() {
        val template = "https://example.org/search?q=%s"
        val preferences = AppPreferences.newBuilder().addCustomSearchEngines(
            CustomSearchEngine.newBuilder()
                .setId("custom")
                .setName("Example")
                .setSearchUrlTemplate(template)
                .build()
        ).build()
        assertEquals(true, SearchEngines.isValidTemplate(template))
        assertEquals(false, SearchEngines.isValidTemplate("javascript:alert(%s)"))
        assertEquals(
            "https://example.org/search?q=caf%C3%A9%20%26%20privacy",
            SearchEngines.url(preferences, "custom", "café & privacy") { "https://astiango.com" },
        )
    }
}
