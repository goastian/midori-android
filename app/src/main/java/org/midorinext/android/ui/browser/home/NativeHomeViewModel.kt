package org.midorinext.android.ui.browser.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.icons.Icon
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import mozilla.components.lib.state.ext.flow
import org.midorinext.android.adblock.BlockingStatistics
import org.midorinext.android.newtab.HomeStoriesRepository
import org.midorinext.android.newtab.HomeFaviconRepository
import org.midorinext.android.newtab.HomeStory
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.HomeWallpaperRepository
import org.midorinext.android.newtab.publicHomeUrl
import org.midorinext.android.newtab.isNativeHomeUrl
import org.midorinext.android.newtab.recentHomeSiteKey
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.storage.history.HistoryRepository
import org.midorinext.android.storage.bookmarks.BookmarksRepository
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
    private val bookmarksProvider: dagger.Lazy<BookmarksRepository>,
    private val wallpapers: HomeWallpaperRepository,
    private val store: BrowserStore,
) : ViewModel() {
    val preferences = preferencesRepository.snapshot
    private val recentRefresh = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentVisits by lazy {
        recentRefresh.flatMapLatest { historyProvider.get().recentSitesFlow }.map { pages ->
            pages.mapNotNull { page -> publicHomeUrl(page.uri)?.let { url ->
                HomePage(page.title?.ifBlank { null } ?: recentHomeSiteKey(url).orEmpty(), url)
            } }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

    suspend fun savedBookmarks(): List<HomePage> = bookmarksProvider.get().searchBookmarks("", 100)
        .getOrNull().orEmpty().mapNotNull { bookmark ->
            bookmark.url?.let(::publicHomeUrl)?.let { url ->
                HomePage(bookmark.title?.ifBlank { null } ?: recentHomeSiteKey(url).orEmpty(), url)
            }
        }.distinctBy { it.url }

    val continuation by lazy {
        fun currentContinuation(state: mozilla.components.browser.state.state.BrowserState) = state.tabs
            .filter { !it.content.private && it.id != state.selectedTabId && publicHomeUrl(it.content.url) != null }
            .maxByOrNull { it.lastAccess }
            ?.let { HomeContinuation(it.id, it.content.title.ifBlank { it.content.url }, it.content.url) }
        store.flow().map(::currentContinuation).distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Lazily, currentContinuation(store.state))
    }

    val blockedCount by lazy { statisticsProvider.get().totalBlocked }

    fun cachedWallpaper(wallpaper: HomeWallpaper) = wallpapers.cached(wallpaper)
    suspend fun loadWallpaper(wallpaper: HomeWallpaper) = wallpapers.load(wallpaper)

    val thumbnailStorage get() = thumbnailProvider.get()
    fun updateThumbnail(tabId: String, bitmap: Bitmap) {
        val tab = store.state.tabs.firstOrNull { it.id == tabId } ?: return
        if (!tab.content.private && isNativeHomeUrl(tab.content.url)) {
            store.dispatch(ContentAction.UpdateThumbnailAction(tabId, bitmap))
        }
    }

    fun favicon(url: String): Flow<Icon?> = flow { emit(faviconProvider.get().load(url)) }
    fun cachedFavicon(url: String): Icon? = faviconProvider.get().cached(url)
    private val mutableStories = MutableStateFlow(HomeStoriesState())
    val stories = mutableStories.asStateFlow()
    private var storiesJob: Job? = null
    private var storiesLanguage: String? = null

    fun stopStories(job: Job) {
        if (storiesJob !== job) return
        job.cancel()
        storiesJob = null
        mutableStories.value = mutableStories.value.copy(loading = false)
    }

    fun initialize() {
        recentRefresh.update { it + 1 }
        if (preferences.value?.homeShortcutsInitialized != true) {
            viewModelScope.launch { preferencesRepository.ensureHomeShortcuts() }
        }
    }

    fun loadStories(language: String, force: Boolean = false): Job {
        storiesJob?.cancel()
        val previousStories = mutableStories.value.stories.takeIf { storiesLanguage == language }.orEmpty()
        storiesLanguage = language
        val job = viewModelScope.launch {
            mutableStories.value = HomeStoriesState(previousStories, loading = true)
            try {
                if (previousStories.isEmpty()) {
                    mutableStories.value = HomeStoriesState(storiesProvider.get().cached(language), loading = true)
                }
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
