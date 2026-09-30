package org.midorinext.android.suggest.providers

import android.content.Context
import android.util.Xml
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import org.midorinext.android.ext.selectedLocale
import org.midorinext.android.suggest.Suggestion
import org.midorinext.android.suggest.SuggestionProvider
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import javax.inject.Inject
import javax.inject.Singleton

/** Public Google Trends RSS is used only when trending suggestions are enabled. */
@Singleton
class TrendingSuggestProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: Client,
) : SuggestionProvider {
    override val supportsEmptyQuery = true

    private var cachedAt = 0L
    private var cachedTitles: List<String> = emptyList()

    override suspend fun getSuggestions(text: String): List<Suggestion> = withContext(Dispatchers.IO) {
        if (text.isNotBlank()) return@withContext emptyList()
        val titles = if (System.currentTimeMillis() - cachedAt < CACHE_MS) {
            cachedTitles
        } else {
            val country = context.selectedLocale().country.ifBlank { "US" }
            val fetched = try {
                client.fetch(Request("https://trends.google.com/trending/rss?geo=$country")).use { response ->
                    if (response.status != 200) emptyList()
                    else response.body.use { parseTitles(it.string()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (fetched.isNotEmpty()) {
                cachedTitles = fetched
                cachedAt = System.currentTimeMillis()
            }
            fetched
        }
        titles.map { Suggestion.SearchSuggestion(this@TrendingSuggestProvider, "", it) }
    }

    private fun parseTitles(xml: String): List<String> {
        val parser = Xml.newPullParser().apply { setInput(StringReader(xml)) }
        val titles = mutableListOf<String>()
        var insideItem = false
        var insideTitle = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT && titles.size < 5) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> insideItem = true
                    "title" -> insideTitle = insideItem
                }
                XmlPullParser.TEXT -> if (insideTitle) {
                    parser.text.trim().takeIf { it.isNotBlank() }?.let(titles::add)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "title" -> insideTitle = false
                    "item" -> insideItem = false
                }
            }
            parser.next()
        }
        return titles
    }

    private companion object {
        const val CACHE_MS = 60 * 60 * 1000L
    }
}
