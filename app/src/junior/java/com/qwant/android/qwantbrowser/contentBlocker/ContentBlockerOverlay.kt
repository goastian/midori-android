package org.midorinext.android.contentBlocker

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.midorinext.android.R

@Composable
fun ContentBlockerOverlay(
    status: ContentBlockerState.Status,
    blockReason: ContentBlockerState.BlockReason,
    @SuppressLint("ModifierParameter") imageModifier: Modifier = Modifier,
    imageScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center
) {
    if (status != ContentBlockerState.Status.ALLOWED) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
            contentAlignment = alignment
        ) {
            if (status == ContentBlockerState.Status.BLOCKED) {
                Image(
                    painter = painterResource(
                        id = when (blockReason) {
                            ContentBlockerState.BlockReason.IP -> R.drawable.dino_ip
                            ContentBlockerState.BlockReason.DOMAIN,
                            ContentBlockerState.BlockReason.URL,
                            ContentBlockerState.BlockReason.SEARCH_ENGINE -> R.drawable.dino_warning
                            else -> R.drawable.dino_timeout
                        }
                    ),
                    contentDescription = "dino",
                    contentScale = imageScale,
                    modifier = imageModifier
                )
            } else if (status == ContentBlockerState.Status.CHECKING) {
                CircularProgressIndicator()
            }
        }
    }
}