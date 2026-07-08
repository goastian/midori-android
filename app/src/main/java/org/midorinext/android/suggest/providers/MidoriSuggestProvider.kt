package org.midorinext.android.suggest.providers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.midorinext.android.BuildConfig
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.storage.MidoriClientProvider
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.support.ktx.android.org.json.toList
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MidoriSuggestProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: Client,
    private val midoriClientProvider: MidoriClientProvider
): SuggestionProvider {
    override suspend fun getSuggestions(text: String): List<Suggestion> = withContext(Dispatchers.IO) {
        if (text.isNotEmpty()) {
            try {
                val request = Request(SuggestFormatUrl.format(
                    midoriClientProvider.clientState.value,
                    context.selectedLocale().toString(),
                    text
                ))
                client.fetch(request).use { response ->
                    if (response.status == 200) {
                        response.body.use { body ->
                            val jsonResponse = JSONObject(body.string())
                            val data = jsonResponse.getJSONObject("data")
                            val standardSuggestions = data.getJSONArray("items")
                            val brandSuggestions = data.getJSONArray("special")

                            return@withContext brandSuggestions
                                .toList<JSONObject>()
                                .filter { it.getString("type") == "brand_suggest" }
                                .take(2) // TODO make this suggestion limit a parameter
                                .mapIndexed { index, jsonObject ->
                                    // TODO Add the option to request any icon to BrowserIcons mozilla component and contribute
                                    var favicon: Bitmap? = null
                                    try {
                                        client.fetch(Request(jsonObject.getString("favicon_url"))).use { response ->
                                            if (response.status == 200) {
                                                response.body.useStream { stream ->
                                                    favicon = BitmapFactory.decodeStream(stream)
                                                }
                                            }
                                        }
                                    } catch (e: IOException) {
                                        Log.e(LOGTAG, "Error fetching ad favicon", e)
                                    } catch (e: Exception) {
                                        Log.e(LOGTAG, "Error decoding ad favicon stream", e)
                                    }

                                    Suggestion.BrandSuggestion(
                                        provider = this@MidoriSuggestProvider,
                                        search = text,
                                        title = jsonObject.getString("name"),
                                        url = jsonObject.getString("url"),
                                        favicon = favicon,
                                        brand = jsonObject.getString("brand"),
                                        domain = jsonObject.getString("domain"),
                                        rank = index + 1,
                                        suggestType = jsonObject.getInt("suggestType")
                                    )
                                }
                                .plus(
                                    standardSuggestions.toList<JSONObject>()
                                        .take(6) // TODO make this suggestion limit a parameter
                                        .map { Suggestion.SearchSuggestion(
                                            provider = this@MidoriSuggestProvider,
                                            search = text,
                                            text = it.getString("value")
                                        ) }
                                )
                        }
                    } else {
                        Log.e(LOGTAG, "Error requesting opensearch results: status not 200 (${response.status})")
                        return@withContext listOf()
                    }
                }
            } catch (e: JSONException) {
                Log.e(LOGTAG, "Error decoding JSON of opensearch results")
                return@withContext listOf()
            } catch (e: IOException) {
                Log.e(LOGTAG, "Error decoding JSON of opensearch results: No internet ?", e)
                return@withContext listOf()
            }
        }
        return@withContext listOf<Suggestion>()
    }


    companion object {
        private const val LOGTAG = "QB_SEARCH_PROVIDER"
        private const val SuggestFormatUrl = "${BuildConfig.QWANT_API_BASE_URL}/suggest?client=%s&locale=%s&version=2&q=%s"
    }
}

