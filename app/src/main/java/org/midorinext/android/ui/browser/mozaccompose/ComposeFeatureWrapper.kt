package org.midorinext.android.ui.browser.mozaccompose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import mozilla.components.support.base.feature.LifecycleAwareFeature

@Composable
fun ComposeFeatureWrapper(
    feature: LifecycleAwareFeature,
    onBackPressed: @Composable () -> Unit = {}
) {
    DisposableEffect(true) {
        feature.start()
        onDispose {
            feature.stop()
        }
    }

    onBackPressed()
}