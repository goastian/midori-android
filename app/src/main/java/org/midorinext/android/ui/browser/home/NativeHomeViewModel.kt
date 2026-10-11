package org.midorinext.android.ui.browser.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.icons.Icon
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.adblock.BlockingStatistics
import org.midorinext.android.newtab.HomeStoriesRepository
import org.midorinext.android.newtab.HomeFaviconRepository
import org.midorinext.android.newtab.HomeStory
import org.midorinext.android.newtab.publicHomeUrl
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.AppPreferencesSerializer
import org.midorinext.android.storage.history.HistoryRepository
import javax.inject.Inject

data class HomePage(val title: String, val url: String)
data class HomeContinuation(val tabId: String, val title: String, val url: String)
data class HomeStoriesState(val stories: List<HomeStory> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)

@HiltViewModel
class NativeHomeViewModel @Inject constructor(
    val preferencesRepository: AppPreferencesRepository,
    private val historyProvider: dagger.Lazy<HistoryRepository>,
    private val statisticsProvider: dagger.Lazy<BlockingStatistics>,
    private val storiesProvider: dagger.Lazy<HomeStoriesRepository>,
    private val thumbnailProvider: dagger.Lazy<ThumbnailStorage>,
    private val faviconProvider: dagger.Lazy<HomeFaviconRepository>,
    private val store: BrowserStore,
) : ViewModel() {
    val preferences = preferencesRepository.flow.stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(0), AppPreferencesSerializer.defaultValue)

    val recentVisits by lazy {
        historyProvider.get().recentPagesFlow.map { pages ->
            pages.mapNotNull { page -> publicHomeUrl(page.uri)?.let { HomePage(page.title?.ifBlank { null } ?: it, it) } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())
    }

    val continuation by lazy {
        store.flow().map { state -> state.tabs
            .filter { !it.content.private && it.id != state.selectedTabId && publicHomeUrl(it.content.url) != null }
            .maxByOrNull { it.lastAccess }
            ?.let { HomeContinuation(it.id, it.content.title.ifBlank { it.content.url }, it.content.url) }
        }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)
    }

    val blockedCount by lazy { statisticsProvider.get().totalBlocked }

    val thumbnailStorage get() = thumbnailProvider.get()
    fun favicon(url: String): Flow<Icon?> = flow { emit(faviconProvider.get().load(url)) }
    private val mutableStories = MutableStateFlow(HomeStoriesState())
    val stories = mutableStories.asStateFlow()
    private var storiesJob: Job? = null

    fun stopStories() {
        storiesJob?.cancel()
        storiesJob = null
        mutableStories.value = mutableStories.value.copy(loading = false)
    }

    fun initialize() {
        viewModelScope.launch { preferencesRepository.ensureHomeShortcuts() }
    }

    fun loadStories(language: String, force: Boolean = false): Job {
        storiesJob?.cancel()
        val job = viewModelScope.launch {
            mutableStories.value = HomeStoriesState(storiesProvider.get().cached(language), loading = true)
            try {
                mutableStories.value = HomeStoriesState(storiesProvider.get().refresh(language, force))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableStories.value = mutableStories.value.copy(loading = false, failed = true)
            }
        }
        storiesJob = job
        return job
    }
}
