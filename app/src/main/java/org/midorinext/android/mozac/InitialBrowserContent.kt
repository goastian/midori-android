package org.midorinext.android.mozac

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import org.midorinext.android.newtab.BrowserSurface
import org.midorinext.android.newtab.browserSurface

internal suspend fun awaitInitialBrowserContent(
    states: Flow<BrowserState>,
    timeoutMillis: Long = 10_000,
    nativeHomePainted: Flow<Boolean> = flowOf(false),
): Boolean = withTimeoutOrNull(timeoutMillis) {
    states.combine(nativeHomePainted) { state, painted ->
        val content = state.selectedTab?.content
        state.restoreComplete && content != null &&
            when (browserSurface(content.url, content.private)) {
                BrowserSurface.HOME, BrowserSurface.PRIVATE_HOME, BrowserSurface.BLANK -> painted
                BrowserSurface.WEB -> content.firstContentfulPaint && !content.loading
            }
    }.first { it }
} != null
