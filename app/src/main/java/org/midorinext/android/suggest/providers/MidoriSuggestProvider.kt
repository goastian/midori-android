package org.midorinext.android.suggest.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.support.ktx.android.org.json.toList
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.midorinext.android.BuildConfig
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.storage.MidoriClientProvider
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import org.midorinext.android.usecases.SearchEngines
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriSuggestProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: Client,
    private val midoriClientProvider: MidoriClientProvider,
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
        val engineId = engineOverride ?: SearchEngines.selectedId(prefs, private)
        val encoded = Uri.encode(text)
        val custom = prefs.customSearchEnginesList.find { it.id == engineId }
        val url = when (engineId) {
            "astiango" -> (BuildConfig.QWANT_API_BASE_URL + "/suggest?client=%s&locale=%s&version=2&q=%s").format(
                midoriClientProvider.clientState.value,
                context.selectedLocale().toString(),
                encoded,
            )
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
            client.fetch(Request(url)).use { response ->
                if (response.status != 200) return@withContext emptyList()
                response.body.use { body ->
                    val json = body.string()
                    if (engineId == "astiango") parseAstianGo(json, text)
                    else parseOpenSearch(json, text)
                }
            }
        } catch (e: IOException) {
            Log.d(LOGTAG, "Search suggestions unavailable", e)
            emptyList()
        } catch (e: JSONException) {
            Log.d(LOGTAG, "Invalid search suggestion response", e)
            emptyList()
        }
    }

    private fun parseAstianGo(json: String, search: String): List<Suggestion> {
        val data = JSONObject(json).getJSONObject("data")
        val brands = data.optJSONArray("special")?.toList<JSONObject>().orEmpty()
            .filter { it.optString("type") == "brand_suggest" }
            .take(2)
            .mapIndexed { index, item ->
                Suggestion.BrandSuggestion(
                    provider = this,
                    search = search,
                    title = item.getString("name"),
                    url = item.getString("url"),
                    faviconUrl = item.optString("favicon_url").takeIf(String::isNotBlank),
                    brand = item.getString("brand"),
                    domain = item.getString("domain"),
                    rank = index + 1,
                    suggestType = item.optInt("suggestType"),
                )
            }
        val suggestions = data.getJSONArray("items").toList<JSONObject>()
            .take(6)
            .map { Suggestion.SearchSuggestion(this, search, it.getString("value")) }
        return brands + suggestions
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
