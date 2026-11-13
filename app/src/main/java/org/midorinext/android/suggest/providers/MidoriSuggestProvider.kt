package org.midorinext.android.suggest.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionFetcher
import org.midorinext.android.suggest.SuggestionProvider
import org.midorinext.android.usecases.SearchEngines
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriSuggestProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fetcher: SuggestionFetcher,
    private val appPreferencesRepository: AppPreferencesRepository,
) : SuggestionProvider {
    override suspend fun getSuggestions(text: String): List<Suggestion> = getSuggestions(text, null, false)

    suspend fun getSuggestions(
        text: String,
        engineOverride: String?,
        private: Boolean,
    ): List<Suggestion> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()
        val prefs = appPreferencesRepository.flow.first()
        if (prefs.disableSearchSuggestions || (private && !prefs.searchSuggestionsInPrivate)) {
            return@withContext emptyList()
        }
        val engineId = (engineOverride ?: SearchEngines.selectedId(prefs, private))
            .let { if (it == "astiango") "google" else it }
        val encoded = Uri.encode(text)
        val custom = prefs.customSearchEnginesList.find { it.id == engineId }
        val url = when (engineId) {
            "ecosia" -> "https://ac.ecosia.org/autocomplete?q=$encoded&type=list"
            "qwant" -> ("https://api.qwant.com/v3/suggest?client=opensearch&locale=%s&version=2&q=%s")
                .format(context.selectedLocale().toString(), encoded)
            "startpage" -> "https://www.startpage.com/osuggestions?q=$encoded"
            "bing" -> "https://www.bing.com/osjson.aspx?query=$encoded"
            "google" -> "https://www.google.com/complete/search?client=firefox&q=$encoded"
            "duckduckgo" -> "https://ac.duckduckgo.com/ac/?type=list&q=$encoded"
            else -> custom?.suggestionUrlTemplate
                ?.takeIf { SearchEngines.isValidTemplate(it) }
                ?.replace("%s", encoded)
                ?: return@withContext emptyList()
        }
        try {
            val json = fetcher.fetch(url, private) ?: return@withContext emptyList()
            parseOpenSearch(json, text)
        } catch (e: IOException) {
            Log.d(LOGTAG, "Search suggestions unavailable", e)
            emptyList()
        } catch (e: JSONException) {
            Log.d(LOGTAG, "Invalid search suggestion response", e)
            emptyList()
        }
    }

    private fun parseOpenSearch(json: String, search: String): List<Suggestion> {
        val values = if (json.trimStart().startsWith("[")) {
            JSONArray(json).getJSONArray(1)
        } else {
            JSONObject(json).getJSONObject("data").getJSONArray("items")
        }
        return (0 until minOf(values.length(), 6)).mapNotNull { index ->
            val item = values.get(index)
            val value = if (item is JSONObject) item.optString("value") else item.toString()
            value.takeIf { it.isNotBlank() }?.let { Suggestion.SearchSuggestion(this, search, it) }
        }
    }

    companion object {
        private const val LOGTAG = "MIDORI_SEARCH_SUGGEST"
    }
}
