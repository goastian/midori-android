package org.midorinext.android.suggest

interface SuggestionProvider {
    suspend fun getSuggestions(text: String): List<Suggestion>
}