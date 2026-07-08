package org.midorinext.android.ui.browser.toolbar

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ProgressBar(
    loadingProgress: Float,
    modifier: Modifier = Modifier
) {
    if (loadingProgress < 1 && loadingProgress > 0) {
        LinearProgressIndicator(
            progress = { loadingProgress },
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier
        )
    }
}