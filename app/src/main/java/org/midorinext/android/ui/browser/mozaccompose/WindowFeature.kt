package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import mozilla.components.feature.tabs.WindowFeature

@Composable
fun WindowFeature(
    store: BrowserStore,
    tabsUseCases: TabsUseCases
) {
    val feature = remember { WindowFeature(
        store = store,
        tabsUseCases = tabsUseCases
    ) }
    ComposeFeatureWrapper(feature = feature)
}