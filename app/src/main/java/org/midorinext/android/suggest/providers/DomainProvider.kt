package org.midorinext.android.suggest.providers

import android.content.Context
import android.util.Log
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainProvider @Inject constructor(
    @ApplicationContext val context: Context
) : SuggestionProvider {
    val domains: List<String> = try {
        context.assets.open("domains.txt").bufferedReader().use(BufferedReader::readLines)
    } catch (e: Exception) {
        Log.e("QB_DOMAIN_PROVIDER", "Failed to load domains from file")
        listOf()
    }

    override suspend fun getSuggestions(text: String) = domains
        .filter { it.startsWith(text) }
        .take(2) // TODO make this suggestion limit a parameter
        .map { Suggestion.SearchSuggestion(this, text, it) }
}