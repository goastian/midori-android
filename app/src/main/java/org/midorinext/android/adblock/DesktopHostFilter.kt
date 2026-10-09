package org.midorinext.android.adblock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.EngineSession
import java.net.URI
import java.util.Locale
import java.util.WeakHashMap
import javax.inject.Inject
import javax.inject.Singleton

internal class SortedHosts(private val text: String) {
    private val starts: IntArray = IntArray(text.count { it == '\n' }).also { positions ->
        var index = 0
        var start = 0
        text.forEachIndexed { offset, char ->
            if (char == '\n') {
                positions[index++] = start
                start = offset + 1
            }
        }
    }

    fun contains(host: String): Boolean {
        var suffix = host
        while (true) {
            if (containsExact(suffix)) return true
            val dot = suffix.indexOf('.')
            if (dot < 0) return false
            suffix = suffix.substring(dot + 1)
        }
    }

    private fun containsExact(host: String): Boolean {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            val start = starts[middle]
            val end = text.indexOf('\n', start)
            val comparison = compare(host, start, end)
            when {
                comparison < 0 -> high = middle - 1
                comparison > 0 -> low = middle + 1
                else -> return true
            }
        }
        return false
    }

    private fun compare(host: String, start: Int, end: Int): Int {
        val length = minOf(host.length, end - start)
        for (index in 0 until length) {
            val difference = host[index].code - text[start + index].code
            if (difference != 0) return difference
        }
        return host.length - (end - start)
    }
}

internal class DesktopHostRules(blocked: String, allowed: String) {
    private val blockedHosts = SortedHosts(blocked)
    private val allowedHosts = SortedHosts(allowed)

    fun decision(url: String): RuleDecision {
        val uri = runCatching { URI(url) }.getOrNull() ?: return RuleDecision.NONE
        if (uri.scheme != "http" && uri.scheme != "https") return RuleDecision.NONE
        val host = uri.host?.lowercase(Locale.ROOT) ?: return RuleDecision.NONE
        return when {
            allowedHosts.contains(host) -> RuleDecision.ALLOW
            blockedHosts.contains(host) -> RuleDecision.BLOCK
            else -> RuleDecision.NONE
        }
    }

    fun blocks(url: String): Boolean = decision(url) == RuleDecision.BLOCK
}

@Singleton
class DesktopHostFilter @Inject constructor(
    val settings: AdBlockSettings,
    private val sources: NativeFilterSources,
) {
    private val counts = WeakHashMap<EngineSession, Int>()
    private val networkCounts = WeakHashMap<EngineSession, Int>()
    private val sessionHosts = WeakHashMap<EngineSession, String>()
    private val countsRevision = MutableStateFlow(0L)
    val changes = countsRevision.asStateFlow()
    val failedSources = sources.failedSources
    val ruleCounts = sources.ruleCounts
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            merge(settings.state.map { Unit }, sources.revision.map { Unit }).collect {
                countsRevision.update { it + 1 }
            }
        }
    }

    fun beginPage(session: EngineSession, url: String) {
        synchronized(counts) {
            if (counts.remove(session) != null) countsRevision.update { it + 1 }
            if (networkCounts.remove(session) != null) countsRevision.update { it + 1 }
            sessionHosts.remove(session)
            hostOf(url)?.let { sessionHosts[session] = it }
        }
    }

    fun blockedCount(session: EngineSession?): Int =
        if (session == null) 0 else synchronized(counts) { (counts[session] ?: 0) + (networkCounts[session] ?: 0) }

    fun updateNetworkCount(session: EngineSession, count: Int) {
        synchronized(counts) {
            val next = count.coerceIn(0, 1_000_000)
            if (networkCounts[session] == next) return
            networkCounts[session] = next
            countsRevision.update { it + 1 }
        }
    }

    fun blocksSubframe(session: EngineSession, url: String, isSameDomain: Boolean): Boolean {
        if (isSameDomain) return false
        val config = settings.current
        val siteHost = synchronized(counts) { sessionHosts[session] }
        val level = siteHost?.let { config.siteLevels[it] } ?: config.level
        if (sources.decision(config, level, url, siteHost) != RuleDecision.BLOCK) return false
        synchronized(counts) {
            counts[session] = (counts[session] ?: 0) + 1
            countsRevision.update { it + 1 }
        }
        return true
    }

    fun refreshSource(url: String) = sources.refreshAsync(url)
    fun refreshOfficial(id: String) = sources.refreshAsync(id)
    fun refreshAll() = sources.refreshAllAsync()
    fun removeSource(url: String) = sources.removeCustom(url)
}
