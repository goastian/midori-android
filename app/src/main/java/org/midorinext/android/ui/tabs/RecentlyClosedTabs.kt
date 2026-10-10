package org.midorinext.android.ui.tabs

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.feature.tabs.TabsUseCases

private const val MAX_RECENTLY_CLOSED = 10

internal class RecentlyClosedTabs(private val tabsUseCases: TabsUseCases) {
    private val snapshots = MutableStateFlow<List<ClosedTabSnapshot>>(emptyList())

    val count = snapshots.map { it.size }

    fun remember(tabs: List<TabSessionState>) {
        val closed = tabs
            .filter { !it.content.private && it.content.url.isNotBlank() }
            .map { ClosedTabSnapshot(title = it.content.title, url = it.content.url) }
        if (closed.isEmpty()) return

        snapshots.value = (closed + snapshots.value)
            .distinctBy { canonicalUrl(it.url) }
            .take(MAX_RECENTLY_CLOSED)
    }

    fun reopen(): Boolean {
        val closed = snapshots.value.firstOrNull() ?: return false
        tabsUseCases.addTab(
            url = closed.url,
            selectTab = true,
            title = closed.title,
            private = false,
        )
        snapshots.value = snapshots.value.drop(1)
        return true
    }
}

private data class ClosedTabSnapshot(val title: String, val url: String)

internal fun canonicalUrl(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return url.trim()
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host?.lowercase()?.removePrefix("www.").orEmpty()
    val path = uri.path.orEmpty().trimEnd('/')
    val query = uri.query.orEmpty()
    return buildString {
        append(scheme)
        append("://")
        append(host)
        append(path)
        if (query.isNotBlank()) {
            append('?')
            append(query)
        }
    }.ifBlank { url.trim() }
}
