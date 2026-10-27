package org.midorinext.android.newtab

import org.junit.Assert.*
import org.junit.Test

class HomeWallpaperTest {
    @Test
    fun emptyAndRetiredSelectionsResolveToThePhotographicDefault() {
        listOf("", "forest", "dawn", "dunes", "silk", "unknown").forEach { id ->
            assertEquals(id, HomeWallpaper.DEFAULT, HomeWallpaper.fromId(id))
        }
        assertNotNull(HomeWallpaper.DEFAULT.image)
        assertEquals(HomeWallpaper.NONE, HomeWallpaper.fromId("none"))
    }

    @Test
    fun largeNatureAndSpaceCollectionsAndTravelContainDistinctAttributedPhotosWithThumbnails() {
        val photos = HomeWallpaper.entries.filter { it != HomeWallpaper.NONE }
        listOf(HomeWallpaperCategory.NATURE, HomeWallpaperCategory.SPACE).forEach { category ->
            assertTrue(category.name, photos.count { it.category == category } > 20)
        }
        assertTrue(photos.any { it.category == HomeWallpaperCategory.TRAVEL })
        assertEquals(photos.size, photos.map { it.id }.toSet().size)
        assertEquals(photos.size, photos.map { it.image }.toSet().size)
        assertEquals(photos.size, photos.map { it.thumbnail }.toSet().size)
        assertEquals(photos.size, photos.map { it.sourceUrl }.toSet().size)
        photos.forEach { photo ->
            assertEquals(photo, HomeWallpaper.fromId(photo.id))
            assertNotNull(photo.image)
            assertNotNull(photo.thumbnail)
            assertNotEquals(photo.image, photo.thumbnail)
            assertTrue(photo.photographer.orEmpty().isNotBlank())
            assertTrue(photo.sourceUrl.orEmpty().startsWith("https://unsplash.com/photos/"))
        }
    }
}
