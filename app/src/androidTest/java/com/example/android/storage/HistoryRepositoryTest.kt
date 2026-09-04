package com.example.android.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.HistoryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import mozilla.components.concept.storage.PageObservation
import mozilla.components.concept.storage.PageVisit
import mozilla.components.concept.storage.VisitType
import java.io.IOException
import kotlin.jvm.Throws

class HistoryRepositoryTest {
    private lateinit var db: HistoryDatabase
    private lateinit var repository: HistoryRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        repository = HistoryRepository(db, ContentBlockerState())
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
}
