package org.midorinext.android.mozac

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import org.midorinext.android.newtab.MidoriNewTabFeature

internal suspend fun awaitInitialBrowserContent(
    states: Flow<BrowserState>,
    timeoutMillis: Long = 10_000,
): Boolean = withTimeoutOrNull(timeoutMillis) {
    states.first { state ->
        val content = state.selectedTab?.content
        state.restoreComplete && content?.firstContentfulPaint == true && !content.loading &&
            content.url != MidoriNewTabFeature.LOADING_URL
    }
} != null
