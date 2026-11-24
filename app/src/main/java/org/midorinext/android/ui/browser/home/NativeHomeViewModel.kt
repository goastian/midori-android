package org.midorinext.android.ui.browser.home

import android.graphics.Bitmap
import org.midorinext.android.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.icons.Icon
import mozilla.components.browser.thumbnails.storage.ThumbnailStorage
import org.midorinext.android.adblock.BlockingStatistics
import org.midorinext.android.newtab.HomeContinuationMiddleware
import org.midorinext.android.newtab.HomeStoriesRepository
import org.midorinext.android.newtab.HomeFaviconRepository
import org.midorinext.android.newtab.HomeStory
import org.midorinext.android.newtab.HomeAdsRepository
import org.midorinext.android.newtab.HomeAdsBookmark
import org.midorinext.android.newtab.HomeAdDecision
import org.midorinext.android.newtab.HomeAdContent
import org.midorinext.android.newtab.HomeAdExposure
import org.midorinext.android.newtab.HomeWallpaper
import org.midorinext.android.newtab.HomeWallpaperRepository
import org.midorinext.android.newtab.publicHomeUrl
import org.midorinext.android.newtab.isNativeHomeUrl
import org.midorinext.android.newtab.recentHomeSiteKey
import org.midorinext.android.preferences.app.AppPreferencesRepository
import org.midorinext.android.preferences.app.HomeShortcut
import org.midorinext.android.storage.history.HistoryRepository
import org.midorinext.android.storage.bookmarks.BookmarksRepository
import javax.inject.Inject
import java.util.Locale

private const val HOME_STOP_TIMEOUT_MILLIS = 1_000L

data class HomePage(val title: String, val url: String)
data class HomeContinuation(val tabId: String, val title: String, val url: String)
data class HomeStoriesState(val stories: List<HomeStory> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)
data class HomeAdsState(
    val catalog: List<HomeAdsBookmark>? = null,
    val contents: Map<String, HomeAdContent> = emptyMap(),
    val decisions: Map<String, HomeAdDecision> = emptyMap(),
    val unavailable: Set<String> = emptySet(),
    val catalogFailed: Boolean = false,
    val session: Long = 0,
) {
    fun content(key: String): HomeAdContent? = decisions[key]?.takeIf { it.isActive() }?.content() ?: contents[key]
}

@HiltViewModel
class NativeHomeViewModel @Inject constructor(
    val preferencesRepository: AppPreferencesRepository,
    private val historyProvider: dagger.Lazy<HistoryRepository>,
    private val statisticsProvider: dagger.Lazy<BlockingStatistics>,
    private val storiesProvider: dagger.Lazy<HomeStoriesRepository>,
    private val thumbnailProvider: dagger.Lazy<ThumbnailStorage>,
    private val faviconProvider: dagger.Lazy<HomeFaviconRepository>,
    private val bookmarksProvider: dagger.Lazy<BookmarksRepository>,
    private val adsProvider: dagger.Lazy<HomeAdsRepository>,
    private val wallpapers: HomeWallpaperRepository,
    private val store: BrowserStore,
    private val continuationUpdates: HomeContinuationMiddleware,
) : ViewModel() {
    val preferences = preferencesRepository.snapshot
    private val recentRefresh = MutableStateFlow(0L)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentVisits by lazy {
        recentRefresh.flatMapLatest { historyProvider.get().recentSitesFlow }.map { pages ->
            pages.mapNotNull { page -> publicHomeUrl(page.uri)?.let { url ->
                HomePage(page.title?.ifBlank { null } ?: recentHomeSiteKey(url).orEmpty(), url)
            } }
        }.distinctUntilChanged().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(HOME_STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
            emptyList(),
        )
    }

    suspend fun savedBookmarks(): List<HomePage> = bookmarksProvider.get().searchBookmarks("", 100)
        .getOrNull().orEmpty().mapNotNull { bookmark ->
            bookmark.url?.let(::publicHomeUrl)?.let { url ->
                HomePage(bookmark.title?.ifBlank { null } ?: recentHomeSiteKey(url).orEmpty(), url)
            }
        }.distinctBy { it.url }

    val continuation by lazy {
        continuationUpdates.changes.map { currentContinuation(store.state) }.distinctUntilChanged()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(HOME_STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0),
                null,
            )
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

    private val mutableAds = MutableStateFlow(cachedHomeAds(Locale.getDefault()))
    val ads = mutableAds.asStateFlow()
    private var adsScope: CoroutineScope? = null
    private var adsLocale = Locale.ENGLISH
    private val adRequests = mutableMapOf<String, Deferred<HomeAdDecision?>>()
    private val renderedAds = mutableSetOf<String>()
    private val impressedAds = mutableSetOf<String>()
    private val pendingAdEvents = mutableSetOf<String>()
    private val adReadyAt = mutableMapOf<String, Long>()
    private val exposures = mutableMapOf<String, HomeAdExposure>()
    private val pendingAdClicks = mutableSetOf<String>()

    private fun cachedHomeAds(locale: Locale, session: Long = 0): HomeAdsState {
        val repository = adsProvider.get()
        val catalog = repository.cachedCatalog(locale)
        return HomeAdsState(catalog = catalog, contents = repository.cachedContents(locale, catalog.orEmpty()), session = session)
    }

    fun beginHomeAdExposure(ad: HomeAdDecision, position: Int?): HomeAdExposure {
        return HomeAdExposure(ad, position).also { exposures[ad.bookmarkKey] = it }
    }

    fun finishHomeAdExposure(exposure: HomeAdExposure) {
        if (exposures[exposure.ad.bookmarkKey] === exposure) exposures.remove(exposure.ad.bookmarkKey)
        adsProvider.get().finishExposure(exposure)
    }

    fun recordHomeAdAction(key: String, event: String) {
        if (key.isNotEmpty()) adsProvider.get().bookmarkAction(key, event, mutableAds.value.decisions[key])
    }

    suspend fun showHomeAds(locale: Locale): Unit = coroutineScope {
        adRequests.clear()
        renderedAds.clear()
        impressedAds.clear()
        pendingAdEvents.clear()
        adReadyAt.clear()
        pendingAdClicks.clear()
        adsScope = this
        adsLocale = locale
        mutableAds.value = cachedHomeAds(locale, mutableAds.value.session + 1)
        mutableAds.value.contents.keys.forEach { adReadyAt[it] = android.os.SystemClock.elapsedRealtime() }
        try {
            mutableAds.value.catalog?.let { preferencesRepository.applyHomeAdsCatalog(it) }
            val catalog = adsProvider.get().catalog(locale)
            preferencesRepository.applyHomeAdsCatalog(catalog)
            mutableAds.value = mutableAds.value.copy(catalog = catalog,
                contents = adsProvider.get().cachedContents(locale, catalog))
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportHomeAdsFailure(error)
            mutableAds.value = mutableAds.value.copy(catalogFailed = mutableAds.value.catalog == null)
            awaitCancellation()
        } finally {
            if (adsScope === this) {
                adsScope = null
                adRequests.clear()
                renderedAds.clear()
                impressedAds.clear()
                pendingAdEvents.clear()
                adReadyAt.clear()
                pendingAdClicks.clear()
                mutableAds.value = mutableAds.value.copy(decisions = emptyMap(), unavailable = emptySet())
            }
        }
    }

    suspend fun prepareHomeAd(key: String) {
        val scope = adsScope ?: return
        if (mutableAds.value.catalog?.none { it.key == key } != false) return
        if (mutableAds.value.decisions[key]?.isActive() == true) return
        val request = adRequests.getOrPut(key) {
            scope.async {
                try {
                    val ad = adsProvider.get().decision(key, adsLocale)
                    if (ad == null) {
                        mutableAds.value = mutableAds.value.copy(unavailable = mutableAds.value.unavailable + key)
                        return@async null
                    }
                    adReadyAt.putIfAbsent(key, android.os.SystemClock.elapsedRealtime())
                    mutableAds.value = mutableAds.value.copy(decisions = mutableAds.value.decisions + (key to ad),
                        contents = mutableAds.value.contents + (key to ad.content()), unavailable = mutableAds.value.unavailable - key)
                    scope.launch {
                        delay((ad.expiresAt - System.currentTimeMillis()).coerceAtLeast(1))
                        if (mutableAds.value.decisions[key]?.decisionId == ad.decisionId) {
                            adRequests.remove(key)
                            renderedAds.remove(ad.decisionId)
                            impressedAds.remove(ad.decisionId)
                            adReadyAt.remove(key)
                            mutableAds.value = mutableAds.value.copy(decisions = mutableAds.value.decisions - key)
                        }
                    }
                    ad
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    reportHomeAdsFailure(error)
                    adRequests.remove(key)
                    if (key !in mutableAds.value.contents) {
                        mutableAds.value = mutableAds.value.copy(unavailable = mutableAds.value.unavailable + key)
                    }
                    null
                }
            }
        }
        request.await()
    }

    suspend fun renderHomeAd(ad: HomeAdDecision): Boolean = homeAdEvent(ad, "render", renderedAds) {
        adsProvider.get().render(ad)
    }

    suspend fun impressHomeAd(ad: HomeAdDecision): Boolean {
        if (ad.decisionId !in renderedAds) return false
        return homeAdEvent(ad, "impression", impressedAds) { adsProvider.get().impression(ad) }
    }

    private suspend fun homeAdEvent(ad: HomeAdDecision, event: String, accepted: MutableSet<String>, send: suspend () -> Boolean): Boolean {
        if (!ad.isActive() || mutableAds.value.decisions[ad.bookmarkKey]?.decisionId != ad.decisionId) return false
        if (ad.decisionId in accepted) return true
        val key = "$event:${ad.decisionId}"
        if (!pendingAdEvents.add(key)) return false
        return try {
            send().also { if (it) accepted.add(ad.decisionId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { false }
        finally { pendingAdEvents.remove(key) }
    }

    fun openHomeShortcut(site: HomeShortcut, onOpenUrl: (String) -> Unit): Boolean {
        val ad = mutableAds.value.decisions[site.adsBookmarkKey]?.takeIf { it.isActive() }
        if (ad == null) {
            if (!site.adsDefault && (site.adsBookmarkKey in mutableAds.value.unavailable ||
                    mutableAds.value.catalog?.none { it.key == site.adsBookmarkKey } == true)) {
                onOpenUrl(site.url)
                return true
            }
            if (site.adsBookmarkKey.isNotEmpty() && preferences.value?.homeAdsCatalogInitialized == true) {
                val scope = adsScope ?: return false
                if (!pendingAdClicks.add(site.adsBookmarkKey)) return false
                scope.launch {
                    try {
                        prepareHomeAd(site.adsBookmarkKey)
                        if (mutableAds.value.decisions[site.adsBookmarkKey]?.isActive() == true ||
                            !site.adsDefault && site.adsBookmarkKey in mutableAds.value.unavailable) openHomeShortcut(site, onOpenUrl)
                    } finally { pendingAdClicks.remove(site.adsBookmarkKey) }
                }
                return true
            }
            onOpenUrl(site.url)
            return true
        }
        val latency = (android.os.SystemClock.elapsedRealtime() - (adReadyAt[site.adsBookmarkKey] ?: 0L)).coerceIn(0, 600_000)
        if (latency < 500) return false
        val separator = if ('?' in ad.destinationUrl) '&' else '?'
        val exposure = exposures[site.adsBookmarkKey]
        exposure?.endReason = "navigation"
        val exposureQuery = exposure?.let { "&exposure_id=${it.id}" }.orEmpty()
        onOpenUrl(ad.destinationUrl + separator + "interaction_ms=$latency&click_device_type=${adsProvider.get().deviceType}&click_event_id=${java.util.UUID.randomUUID()}" + exposureQuery)
        return true
    }

    fun dismissHomeAd(site: HomeShortcut) {
        val ad = mutableAds.value.decisions[site.adsBookmarkKey]?.takeIf { it.isActive() } ?: return
        adsScope?.launch {
            try { adsProvider.get().dismissed(ad) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { }
        }
    }

    private fun reportHomeAdsFailure(error: Exception) {
        if (!BuildConfig.DEBUG) return
        val location = error.stackTrace.firstOrNull()
        android.util.Log.w("HomeAds", "${error.javaClass.simpleName} at ${location?.className}:${location?.lineNumber}")
    }

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

private fun currentContinuation(state: BrowserState): HomeContinuation? {
    var latest: TabSessionState? = null
    for (tab in state.tabs) {
        if (tab.content.private || tab.id == state.selectedTabId) continue
        if (latest?.let { tab.lastAccess <= it.lastAccess } == true) continue
        if (publicHomeUrl(tab.content.url) != null) latest = tab
    }
    return latest?.let { HomeContinuation(it.id, it.content.title.ifBlank { it.content.url }, it.content.url) }
}
