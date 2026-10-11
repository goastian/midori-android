package org.midorinext.android.newtab

import org.junit.Assert.*
import org.junit.Test

class HomeStoriesTest {
    @Test
    fun parsesPublisherTitlesLinksAndThumbnailsWithoutDuplicates() {
        val xml = """<rss xmlns:media="http://search.yahoo.com/mrss/"><channel>
            <item><title><![CDATA[Privacy & browsers]]></title><link>https://example.org/story</link>
                <media:thumbnail url="https://example.org/image.jpg"/></item>
            <item><title>Duplicate</title><link>https://example.org/story</link></item>
            <item><title>Unsafe</title><link>javascript:alert(1)</link></item>
            <item><title>Missing URL</title></item>
            </channel></rss>"""
        val story = parseHomeStories(xml, "Publisher").single()
        assertEquals("Privacy & browsers", story.title)
        assertEquals("https://example.org/story", story.url)
        assertEquals("https://example.org/image.jpg", story.imageUrl)
        assertEquals("Publisher", story.publisher)
    }

    @Test
    fun malformedFeedsFailAndFeedsWithoutUsableItemsAreEmpty() {
        assertTrue(runCatching { parseHomeStories("not XML", "Publisher") }.isFailure)
        assertTrue(parseHomeStories("<rss><channel/></rss>", "Publisher").isEmpty())
    }
}
