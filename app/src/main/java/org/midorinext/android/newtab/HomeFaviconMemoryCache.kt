package org.midorinext.android.newtab

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class HomeFaviconMemoryCache<T>(
    private val sizeOf: (T) -> Int,
    private val maxEntries: Int = 64,
    private val maxBytes: Int = 8 * 1024 * 1024,
    private val lifetimeMillis: Long = 86_400_000,
    private val failureLifetimeMillis: Long = 300_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry<T>(val value: T?, val bytes: Int, val expiresAt: Long)
    private class Loading(val mutex: Mutex = Mutex(), var readers: Int = 0)
    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry<T>>(16, 0.75f, true)
    private val loading = mutableMapOf<String, Loading>()
    private var bytes = 0

    fun peek(key: String): T? = synchronized(lock) { cached(key)?.value }

    suspend fun getOrLoad(key: String, load: suspend () -> T?): T? {
        val pending = synchronized(lock) {
            cached(key)?.let { return it.value }
            loading.getOrPut(key) { Loading() }.also { it.readers++ }
        }
        try {
            return pending.mutex.withLock {
                synchronized(lock) { cached(key)?.let { return@withLock it.value } }
                val value = load()
                synchronized(lock) {
                    val weight = value?.let(sizeOf)?.coerceAtLeast(1) ?: 1
                    if (weight <= maxBytes) {
                        val lifetime = if (value == null) failureLifetimeMillis else lifetimeMillis
                        entries.put(key, Entry(value, weight, now() + lifetime))?.let { bytes -= it.bytes }
                        bytes += weight
                        while (entries.size > maxEntries || bytes > maxBytes) {
                            val eldest = entries.entries.iterator()
                            bytes -= eldest.next().value.bytes
                            eldest.remove()
                        }
                    }
                }
                value
            }
        } finally {
            synchronized(lock) {
                if (--pending.readers == 0) loading.remove(key)
            }
        }
    }

    fun clear() = synchronized(lock) {
        entries.clear()
        bytes = 0
    }

    private fun cached(key: String): Entry<T>? {
        val entry = entries[key] ?: return null
        if (now() < entry.expiresAt) return entry
        entries.remove(key)
        bytes -= entry.bytes
        return null
    }
}
