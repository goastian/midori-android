package org.midorinext.android.suggest

interface SuggestionProvider {
    val supportsEmptyQuery: Boolean get() = false
    suspend fun getSuggestions(text: String): List<Suggestion>
}
