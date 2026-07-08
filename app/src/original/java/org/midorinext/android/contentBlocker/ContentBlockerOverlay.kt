package org.midorinext.android.contentBlocker

import android.annotation.SuppressLint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

@Suppress("UNUSED_PARAMETER")
@Composable
fun ContentBlockerOverlay(
    status: ContentBlockerState.Status,
    blockReason: ContentBlockerState.BlockReason,
    @SuppressLint("ModifierParameter") imageModifier: Modifier = Modifier,
    imageScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center
) {}