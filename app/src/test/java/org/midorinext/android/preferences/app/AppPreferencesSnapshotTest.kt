package org.midorinext.android.preferences.app

import androidx.datastore.core.DataStore
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AppPreferencesSnapshotTest {
    @Test(timeout = 5_000)
    fun startupSnapshotWaitsForPersistedPreferencesWithoutPublishingDefaults() = runBlocking {
        val persisted = AppPreferencesSerializer.defaultValue.toBuilder()
            .setAppearance(Appearance.DARK)
            .setToolbarPosition(ToolbarPosition.TOP)
            .setHomeWallpaperId("forest")
            .setHideHomeStories(true)
            .build()
        val store = DelayedPreferencesStore(persisted)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = AppPreferencesRepository(store, scope)
            store.readStarted.await()
            val firstRead = async(start = CoroutineStart.UNDISPATCHED) { repository.flow.first() }

            assertNull(repository.snapshot.value)
            assertFalse(firstRead.isCompleted)
            store.allowRead.complete(Unit)

            assertEquals(persisted, firstRead.await())
            assertEquals(persisted, repository.snapshot.first { it != null })
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun writesRemainImmediatelyReadableAndRefreshTheSharedSnapshot() = runBlocking {
        val initial = AppPreferencesSerializer.defaultValue.toBuilder()
            .setAppearance(Appearance.DARK)
            .build()
        val store = DelayedPreferencesStore(initial).apply { allowRead.complete(Unit) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = AppPreferencesRepository(store, scope)
            assertEquals(initial, repository.snapshot.first { it != null })

            repository.updateAppearance(Appearance.LIGHT)
            repository.updateToolbarPosition(ToolbarPosition.TOP)
            val expected = initial.toBuilder()
                .setAppearance(Appearance.LIGHT)
                .setToolbarPosition(ToolbarPosition.TOP)
                .build()

            assertEquals(expected, repository.flow.first())
            assertEquals(expected, repository.snapshot.value)
            assertEquals(expected, repository.snapshot.first { it == expected })
        } finally {
            scope.cancel()
        }
    }

    @Test(timeout = 5_000)
    fun snapshotRecoversFromAnInitialReadFailureAndContinuesReceivingWrites() = runBlocking {
        val persisted = AppPreferencesSerializer.defaultValue.toBuilder()
            .setAppearance(Appearance.DARK)
            .setHomeWallpaperId("forest")
            .build()
        val preferences = MutableStateFlow(persisted)
        val attempts = AtomicInteger()
        val store = object : DataStore<AppPreferences> {
            override val data = flow {
                if (attempts.getAndIncrement() == 0) throw IOException("Temporary read failure")
                emitAll(preferences)
            }
            override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences =
                transform(preferences.value).also { preferences.value = it }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val repository = AppPreferencesRepository(store, scope)
            assertEquals(AppPreferencesSerializer.defaultValue, repository.snapshot.value)
            assertEquals(persisted, repository.snapshot.first { it == persisted })

            repository.updateToolbarPosition(ToolbarPosition.TOP)
            val updated = persisted.toBuilder().setToolbarPosition(ToolbarPosition.TOP).build()
            assertEquals(updated, repository.flow.first())
            assertEquals(updated, repository.snapshot.first { it == updated })
        } finally {
            scope.cancel()
        }
    }

    private class DelayedPreferencesStore(initial: AppPreferences) : DataStore<AppPreferences> {
        val readStarted = CompletableDeferred<Unit>()
        val allowRead = CompletableDeferred<Unit>()
        private val preferences = MutableStateFlow(initial)
        override val data = flow {
            readStarted.complete(Unit)
            allowRead.await()
            emitAll(preferences)
        }

        override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences =
            transform(preferences.value).also { preferences.value = it }
    }
}
