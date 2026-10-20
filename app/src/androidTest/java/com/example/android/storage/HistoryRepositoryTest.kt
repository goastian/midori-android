package com.example.android.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.HistoryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import org.midorinext.android.storage.history.Page
import org.midorinext.android.storage.history.Visit
import java.io.IOException
import kotlin.jvm.Throws

class HistoryRepositoryTest {
    private lateinit var db: HistoryDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        repository = HistoryRepository(db)
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun empty_at_start() = runTest {
        assertThat(repository.getVisited().size, equalTo(0))
    }

    @Test
    fun synchronous_suggestions_match_title_and_are_recent_first() = runTest {
        val now = System.currentTimeMillis()
        repository.recordVisitWithTimestamp(
            "https://older.example",
            PageVisit(VisitType.TYPED),
            timestamp = now - 1_000,
        )
        repository.recordObservation(
            "https://older.example",
            PageObservation(title = "Example older"),
        )
        repository.recordVisitWithTimestamp(
            "https://newer.example",
            PageVisit(VisitType.LINK),
            timestamp = now,
        )
        repository.recordObservation(
            "https://newer.example",
            PageObservation(title = "Example newer"),
        )

        val results = withContext(Dispatchers.IO) {
            repository.getSuggestions("Example", limit = 2)
        }

        assertThat(
            results.map { result -> result.url },
            equalTo(listOf("https://newer.example", "https://older.example")),
        )
        assertThat(results.map { result -> result.score }, equalTo(listOf(2, 1)))
    }

    @Test
    fun synchronous_suggestions_reject_invalid_requests() = runTest {
        assertThat(repository.getSuggestions("", limit = 2).isEmpty(), equalTo(true))
        assertThat(repository.getSuggestions("example", limit = 0).isEmpty(), equalTo(true))
    }

    @Test
    fun recent_sites_skip_redirects_and_downloads_and_keep_separate_services() = runBlocking {
        val now = System.currentTimeMillis()
        val visits = listOf(
            Triple("https://download.example/file", VisitType.DOWNLOAD, "Download"),
            Triple("https://accounts.google.com/ServiceLogin", VisitType.REDIRECT_TEMPORARY, "Login"),
            Triple("https://gmail.com/", VisitType.REDIRECT_PERMANENT, "Gmail redirect"),
            Triple("https://mail.google.com/mail/u/0/#inbox", VisitType.RELOAD, "Inbox"),
            Triple("https://mail.google.com/mail/u/0/", VisitType.LINK, "Gmail"),
            Triple("https://drive.google.com/drive/", VisitType.LINK, "Drive"),
            Triple("https://outlook.live.com/mail/", VisitType.LINK, "Outlook"),
        )
        visits.forEachIndexed { index, (url, type, title) ->
            repository.recordVisitWithTimestamp(url, PageVisit(type), now - index)
            repository.recordObservation(url, PageObservation(title = title))
        }

        val sites = repository.recentSitesFlow.first()

        assertEquals(listOf("Inbox", "Drive", "Outlook"), sites.map { it.title })
        assertEquals("https://mail.google.com/mail/u/0/#inbox", sites.first().uri)
    }

    @Test
    fun recent_sites_find_older_destinations_after_many_pages_from_one_site() = runBlocking {
        val now = System.currentTimeMillis()
        repeat(40) { index ->
            repository.recordVisitWithTimestamp("https://www.example.org/page/$index", PageVisit(VisitType.LINK), now - index)
        }
        repository.recordVisitWithTimestamp("http://example.org/older", PageVisit(VisitType.LINK), now - 41)
        repository.recordVisitWithTimestamp("https://second.example/", PageVisit(VisitType.LINK), now - 42)
        repository.recordVisitWithTimestamp("https://third.example/", PageVisit(VisitType.LINK), now - 43)

        assertEquals(
            listOf("https://www.example.org/page/0", "https://second.example/", "https://third.example/"),
            repository.recentSitesFlow.first().map { it.uri },
        )
    }

    @Test
    fun recent_snapshot_stays_stable_during_navigation_and_reloads_on_collection() = runBlocking {
        val now = System.currentTimeMillis()
        val first = "https://first.example/"
        val second = "https://second.example/"
        repository.recordVisitWithTimestamp(first, PageVisit(VisitType.LINK), now - 1)
        repository.recordObservation(first, PageObservation(title = "First"))
        val updates = Channel<List<Page>>(Channel.UNLIMITED)
        val collector = launch { repository.recentSitesFlow.collect { updates.send(it) } }
        try {
            assertEquals(listOf("First"), withTimeout(5_000) { updates.receive() }.map { it.title })
            repository.recordVisitWithTimestamp(second, PageVisit(VisitType.LINK), now)
            repository.recordObservation(first, PageObservation(title = "Updated title"))
            assertNull(withTimeoutOrNull(250) { updates.receive() })

            val reopened = repository.recentSitesFlow.first()
            assertEquals(listOf(second, first), reopened.map { it.uri })
            assertEquals("Updated title", reopened.last().title)

            repository.deleteVisitsFor("https://unrelated.example/")
            assertEquals(listOf("First"), withTimeout(5_000) { updates.receive() }.map { it.title })
            repository.deleteVisitsFor(first)
            assertEquals(emptyList<Page>(), withTimeout(5_000) { updates.receive() })
        } finally {
            collector.cancelAndJoin()
            updates.close()
        }
        assertEquals(listOf(second), repository.recentSitesFlow.first().map { it.uri })
    }

    @Test
    fun every_history_deletion_updates_an_open_snapshot() = runBlocking {
        val url = "https://deleted.example/"
        val timestamp = System.currentTimeMillis() - HistoryRepository.TIME_LIMIT - 1_000
        val deletions: List<suspend () -> Unit> = listOf(
            { repository.deleteVisit(url, timestamp) },
            { repository.deleteVisitsFor(url) },
            { repository.deleteVisitsBetween(timestamp, timestamp) },
            { repository.deleteVisitsSince(timestamp) },
            { repository.runMaintenance(0U) },
            { repository.deleteEverything() },
        )
        for (delete in deletions) {
            db.historyDao().insertIfNeeded(Page(url))
            db.historyDao().insert(Visit(url, timestamp, VisitType.LINK))
            val updates = Channel<List<Page>>(Channel.UNLIMITED)
            val collector = launch { repository.recentSitesFlow.collect { updates.send(it) } }
            try {
                assertEquals(listOf(url), withTimeout(5_000) { updates.receive() }.map { it.uri })
                delete()
                assertEquals(emptyList<Page>(), withTimeout(5_000) { updates.receive() })
            } finally {
                collector.cancelAndJoin()
                updates.close()
            }
        }
    }
}
