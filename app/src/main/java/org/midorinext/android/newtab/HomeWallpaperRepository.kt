package org.midorinext.android.newtab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class HomeWallpaperAsset(val wallpaper: HomeWallpaper, val bitmap: Bitmap?)

@Singleton
class HomeWallpaperRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    memoryBudget: org.midorinext.android.mozac.BrowserMemoryBudget,
) {
    private val cache = LruCache<HomeWallpaper, HomeWallpaperAsset>(2)
    private val decoding = Mutex()

    init {
        memoryBudget.registerCache(this) { repository, level ->
            if (org.midorinext.android.mozac.shouldClearBrowserMemory(level)) repository.clearMemory()
        }
    }

    fun cached(wallpaper: HomeWallpaper): HomeWallpaperAsset? =
        if (wallpaper.image == null) HomeWallpaperAsset(wallpaper, null) else cache.get(wallpaper)

    suspend fun load(wallpaper: HomeWallpaper): HomeWallpaperAsset =
        cached(wallpaper) ?: withContext(Dispatchers.IO) {
            decoding.withLock {
                cached(wallpaper) ?: HomeWallpaperAsset(
                    wallpaper,
                    wallpaper.image?.let { BitmapFactory.decodeResource(context.resources, it) },
                ).also { cache.put(wallpaper, it) }
            }
        }

    fun clearMemory() = cache.evictAll()
}
