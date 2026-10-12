package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class TabsViewPreferencesTest {
    @Test
    fun threeAndFourQueuedClicksPersistEveryCycleFromDefaultView() = runBlocking {
        for ((clicks, expected) in listOf(3 to TabsViewOption.GRID, 4 to TabsViewOption.CAROUSEL)) {
            withPreferences { store, repository, file ->
                assertEquals(TabsViewOption.GRID, store.data.first().tabsView)
                withTimeout(10_000) {
                    coroutineScope {
                        val entered = CompletableDeferred<Unit>()
                        val release = CompletableDeferred<Unit>()
                        val pendingWrite = launch {
                            store.updateData { current ->
                                entered.complete(Unit)
                                release.await()
                                current
                            }
                        }
                        entered.await()
                        val updates = List(clicks) {
                            launch(start = CoroutineStart.UNDISPATCHED) { repository.cycleTabsView() }
                        }
                        release.complete(Unit)
                        updates.joinAll()
                        pendingWrite.join()
                    }
                }
                assertEquals(expected, store.data.first().tabsView)
                assertEquals(expected, AppPreferences.parseFrom(file.readBytes()).tabsView)
            }
        }
    }

    @Test
    fun savedListPreferenceCyclesFromStoredViewAndPreservesOtherPreferences() = runBlocking {
        val saved = AppPreferencesSerializer.defaultValue.toBuilder()
            .setTabsView(TabsViewOption.LIST)
            .setOpenBlankNewTab(true)
            .build()
        withPreferences(saved) { store, repository, file ->
            assertEquals(TabsViewOption.LIST, store.data.first().tabsView)

            repository.cycleTabsView()

            val restored = AppPreferences.parseFrom(file.readBytes())
            assertEquals(TabsViewOption.GRID, restored.tabsView)
            assertEquals(saved.toBuilder().setTabsView(TabsViewOption.GRID).build(), restored)
        }
    }

    @Test
    fun unknownSavedViewCyclesFromVisibleGridFallback() = runBlocking {
        val saved = AppPreferencesSerializer.defaultValue.toBuilder().setTabsViewValue(99).build()
        withPreferences(saved) { store, repository, file ->
            assertEquals(TabsViewOption.UNRECOGNIZED, store.data.first().tabsView)

            repository.cycleTabsView()

            assertEquals(TabsViewOption.CAROUSEL, AppPreferences.parseFrom(file.readBytes()).tabsView)
        }
    }

    private suspend fun withPreferences(
        initial: AppPreferences? = null,
        block: suspend (DataStore<AppPreferences>, AppPreferencesRepository, File) -> Unit,
    ) {
        val directory = kotlin.io.path.createTempDirectory("tabs-view-preferences").toFile()
        val file = File(directory, "preferences.pb")
        if (initial != null) file.writeBytes(initial.toByteArray())
        val job = SupervisorJob()
        val store = DataStoreFactory.create(
            serializer = AppPreferencesSerializer,
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            block(store, AppPreferencesRepository(store), file)
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }
}
