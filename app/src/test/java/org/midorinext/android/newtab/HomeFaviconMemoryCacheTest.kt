package org.midorinext.android.newtab

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class HomeFaviconMemoryCacheTest {
    @Test
    fun warmIconsAreImmediatelyAvailableWithoutRepeatingTheLoad() = runBlocking {
        val cache = HomeFaviconMemoryCache<String>(sizeOf = String::length)
        var loads = 0
        assertEquals("icon", cache.getOrLoad("origin") { loads++; "icon" })
        assertEquals("icon", cache.peek("origin"))
        assertEquals("icon", cache.getOrLoad("origin") { loads++; "changed" })
        assertEquals(1, loads)
    }

    @Test
    fun failedLoadsAreRetriedAfterExpiryAndSuccessfulIconsCanBeInvalidated() = runBlocking {
        var time = 0L
        val cache = HomeFaviconMemoryCache<String>(sizeOf = String::length,
            failureLifetimeMillis = 100, now = { time })
        var loads = 0
        assertNull(cache.getOrLoad("origin") { loads++; null })
        assertNull(cache.getOrLoad("origin") { loads++; "too early" })
        assertEquals(1, loads)
        time = 100
        assertEquals("icon", cache.getOrLoad("origin") { loads++; "icon" })
        assertEquals(2, loads)
        cache.clear()
        assertNull(cache.peek("origin"))
        assertEquals("fresh", cache.getOrLoad("origin") { "fresh" })
    }

    @Test
    fun boundedCacheEvictsTheLeastRecentlyUsedIcon() = runBlocking {
        val cache = HomeFaviconMemoryCache<String>(sizeOf = String::length, maxBytes = 4)
        cache.getOrLoad("first") { "aa" }
        cache.getOrLoad("second") { "bb" }
        cache.peek("first")
        cache.getOrLoad("third") { "cc" }
        assertEquals("aa", cache.peek("first"))
        assertNull(cache.peek("second"))
        assertEquals("cc", cache.peek("third"))
    }

    @Test
    fun concurrentReadersShareOneLoad() = runBlocking {
        val cache = HomeFaviconMemoryCache<String>(sizeOf = String::length)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var loads = 0
        val first = async { cache.getOrLoad("origin") { loads++; started.complete(Unit); finish.await(); "icon" } }
        started.await()
        val second = async { cache.getOrLoad("origin") { loads++; "duplicate" } }
        yield()
        finish.complete(Unit)
        assertEquals("icon", first.await())
        assertEquals("icon", second.await())
        assertEquals(1, loads)
    }

    @Test
    fun cancellingOneReaderDoesNotCancelAnotherReader() = runBlocking {
        val cache = HomeFaviconMemoryCache<String>(sizeOf = String::length)
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val first = async { cache.getOrLoad("origin") { started.complete(Unit); never.await(); "cancelled" } }
        started.await()
        val second = async { cache.getOrLoad("origin") { "icon" } }
        yield()
        first.cancelAndJoin()
        assertEquals("icon", second.await())
        assertEquals("icon", cache.peek("origin"))
    }
}
