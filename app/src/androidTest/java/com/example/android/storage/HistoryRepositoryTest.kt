package com.example.android.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.midorinext.android.contentBlocker.ContentBlockerState
import org.midorinext.android.storage.history.HistoryDatabase
import org.midorinext.android.storage.history.HistoryRepository
import kotlinx.coroutines.test.runTest
import org.hamcrest.CoreMatchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
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
}