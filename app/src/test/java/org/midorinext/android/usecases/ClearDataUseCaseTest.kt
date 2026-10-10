package org.midorinext.android.usecases

import androidx.datastore.core.DataStore
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.storage.HistoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.midorinext.android.preferences.app.AppPreferences
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.ui.tabs.tabsTestStore

class ClearDataUseCaseTest {
    @Test
    fun firstUseReadsPreferencesAndRemovesPrivateTabsWithoutClearingNormalTabs() = runBlocking {
        val normal = createTab("https://example.org/normal")
        val private = createTab("https://example.org/private", private = true)
        val store = tabsTestStore(BrowserState(tabs = listOf(normal, private), selectedTabId = private.id))
        val preferences = MutableStateFlow(AppPreferences.getDefaultInstance())
        val datastore = object : DataStore<AppPreferences> {
            override val data = preferences
            override suspend fun updateData(transform: suspend (AppPreferences) -> AppPreferences): AppPreferences =
                transform(preferences.value).also { preferences.value = it }
        }
        val clear = ClearDataUseCase(AppPreferencesRepository(datastore), unused(Engine::class.java), store, unused(HistoryStorage::class.java))
        var success = false
        clear.clear { success = it }

        assertTrue(success)
        assertEquals(listOf(normal.id), store.state.tabs.map { it.id })

        preferences.value = preferences.value.toBuilder().setClearDataTabs(true).build()
        clear.clear { success = it }

        assertTrue(success)
        assertTrue(store.state.tabs.isEmpty())
    }

    private fun <T> unused(type: Class<T>): T = type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        error("Unexpected operation: ${method.name}")
    })
}
