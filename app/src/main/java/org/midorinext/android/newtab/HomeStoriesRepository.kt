package org.midorinext.android.newtab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
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
class HomeStoriesRepository @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun cached(language: String): List<HomeStory> = withContext(Dispatchers.IO) {
        val cache = cache(language)
        if (!cache.isFile) emptyList() else runCatching {
            parseHomeStories(cache.readText(), publisher(language))
        }.getOrDefault(emptyList())
    }

    suspend fun refresh(language: String, force: Boolean = false): List<HomeStory> = withContext(Dispatchers.IO) {
        val cache = cache(language)
        if (!force && cache.isFile && System.currentTimeMillis() - cache.lastModified() < 3_600_000) {
            val stories = parseHomeStories(cache.readText(), publisher(language))
            if (stories.isNotEmpty()) return@withContext stories
        }
        val feed = if (language == "es") "https://feeds.bbci.co.uk/mundo/rss.xml"
            else "https://feeds.bbci.co.uk/news/world/rss.xml"
        val connection = URL(feed).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    check(output.size() + count <= 1_048_576)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val xml = bytes.toString(Charsets.UTF_8)
            val stories = parseHomeStories(xml, publisher(language))
            check(stories.isNotEmpty())
            val pending = File.createTempFile("home-stories-", ".pending", context.cacheDir)
            pending.writeText(xml)
            check(pending.renameTo(cache))
            stories
        } finally {
            connection.disconnect()
        }
    }

    private fun cache(language: String) = File(context.cacheDir, "home-stories-${if (language == "es") "es" else "en"}.xml")
    private fun publisher(language: String) = if (language == "es") "BBC Mundo" else "BBC News"
}
