package org.midorinext.android.newtab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory

data class HomeStory(val title: String, val url: String, val publisher: String, val imageUrl: String?)

internal fun parseHomeStories(xml: String, publisher: String): List<HomeStory> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
    }
    val builder = factory.newDocumentBuilder().apply {
        setEntityResolver { _, _ -> InputSource(StringReader("")) }
    }
    val items = builder.parse(InputSource(StringReader(xml))).getElementsByTagName("item")
    return (0 until minOf(items.length, 12)).mapNotNull { index ->
        val item = items.item(index) as org.w3c.dom.Element
        val title = item.getElementsByTagName("title").item(0)?.textContent?.trim().orEmpty()
        val url = item.getElementsByTagName("link").item(0)?.textContent?.let(::publicHomeUrl)
        val image = (item.getElementsByTagNameNS("*", "thumbnail").item(0) as? org.w3c.dom.Element)
            ?.getAttribute("url")?.let(::publicHomeUrl)
        if (title.isBlank() || url == null) null else HomeStory(title, url, publisher, image)
    }.distinctBy { it.url }
}

@Singleton
class HomeStoriesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clientProvider: dagger.Lazy<Client>,
) {
    private val refreshLock = Mutex()

    suspend fun cached(language: String): List<HomeStory> = withContext(Dispatchers.IO) {
        val cache = cache(language)
        if (!cache.isFile || cache.length() > MAX_FEED_SIZE) emptyList() else runCatching {
            parseHomeStories(cache.readText(), publisher(language))
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(language: String, force: Boolean = false): List<HomeStory> = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val cache = cache(language)
            if (!force && cache.isFile && cache.length() <= MAX_FEED_SIZE && System.currentTimeMillis() - cache.lastModified() < 3_600_000) {
                val stories = runCatching { parseHomeStories(cache.readText(), publisher(language)) }.getOrDefault(emptyList())
                if (stories.isNotEmpty()) return@withLock stories
            }
            val feed = if (language == "es") "https://feeds.bbci.co.uk/mundo/rss.xml"
                else "https://feeds.bbci.co.uk/news/world/rss.xml"
            val request = Request(feed, connectTimeout = 5L to TimeUnit.SECONDS, readTimeout = 5L to TimeUnit.SECONDS,
                cookiePolicy = Request.CookiePolicy.OMIT)
            val xml = clientProvider.get().fetch(request).use { response ->
                check(response.isSuccess)
                response.body.useBufferedReader { reader ->
                    val text = StringBuilder()
                    val buffer = CharArray(8_192)
                    while (true) {
                        ensureActive()
                        val count = reader.read(buffer)
                        if (count == -1) break
                        check(text.length + count <= MAX_FEED_SIZE)
                        text.append(buffer, 0, count)
                    }
                    text.toString()
                }
            }
            ensureActive()
            val stories = parseHomeStories(xml, publisher(language))
            check(stories.isNotEmpty())
            val pending = File.createTempFile("home-stories-", ".pending", context.cacheDir)
            try {
                pending.writeText(xml)
                ensureActive()
                check(pending.renameTo(cache))
                stories
            } finally {
                pending.delete()
            }
        }
    }

    private fun cache(language: String) = File(context.cacheDir, "home-stories-${if (language == "es") "es" else "en"}.xml")
    private fun publisher(language: String) = if (language == "es") "BBC Mundo" else "BBC News"

    private companion object {
        const val MAX_FEED_SIZE = 1_048_576L
    }
}
