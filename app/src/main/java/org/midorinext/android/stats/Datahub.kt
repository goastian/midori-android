package org.midorinext.android.stats

import android.content.Context
import android.util.Log
import org.midorinext.android.BuildConfig
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.storage.MidoriClientProvider
import org.midorinext.android.suggest.Suggestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.MutableHeaders
import mozilla.components.concept.fetch.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Datahub @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: Client,
    private val clientProvider: MidoriClientProvider,
) {
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun brandSuggestClicked(suggestion: Suggestion.BrandSuggestion) {
        call("suggest", BRAND_SUGGEST_PAYLOAD.format(
            clientProvider.clientState.value,
            context.selectedLocale().language,
            context.selectedLocale().toString(),
            suggestion.brand,
            suggestion.search.length,
            suggestion.rank,
            suggestion.search,
            suggestion.suggestType,
            suggestion.url
        ))
    }

    private fun call(endpoint: String, payload: String) {
        scope.launch {
            val request = Request(
                url = BASE_URL + endpoint,
                method = Request.Method.POST,
                headers = MutableHeaders(
                    Pair("Content-Type", "application/json"),
                    Pair("User-Agent", org.mozilla.geckoview.BuildConfig.USER_AGENT_GECKOVIEW_MOBILE)
                ),
                body = Request.Body.fromString(payload)
            )
            try {
                client.fetch(request).use { response ->
                    if (response.status != 204) {
                        Log.e("MIDORI_DATAHUB", "Error sending datahub request:\n" +
                                "endpoint: $endpoint\n" +
                                "payload: $payload\n" +
                                "status: ${response.status}\n" +
                                "body: ${response.body.string()}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MIDORI_DATAHUB", "Error sending datahub request:\n" +
                        "url: ${BASE_URL + endpoint}\n" +
                        "payload: $payload\n" +
                        "exception: $e")
            }
        }
    }

    companion object {
        private const val BASE_URL = "${BuildConfig.QWANT_BASE_URL}/action/"
        private const val BRAND_SUGGEST_PAYLOAD = "{" +
            "\"client\": \"%s\"," +                         // 0: client
            "\"interface_language\": \"%s\"," +             // 1: interface_language
            "\"tgp\": 0," +
            "\"uri\": \"\"," +
            "\"data\": {" +
                "\"ad_type\": \"brand-suggest\"," +
                "\"ad_version\": \"customadserver\"," +
                "\"locale\": \"%s\"," +                     // 2: locale
                "\"brand\": \"%s\"," +                      // 3: ad brand
                "\"count\": %d," +                          // 4: query length
                "\"device\": \"smartphone\"," +
                "\"position\": %d," +                       // 5: ad position
                "\"query\": \"%s\"," +                      // 6: query
                "\"suggest_type\": %d," +                   // 7: ad suggestType
                "\"tgp\": 0," +
                "\"type\": \"ad\"," +
                "\"url\": \"%s\"" +                         // 8: ad URL
            "}" +
        "}"
    }
}