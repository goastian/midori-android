package org.midorinext.android.ui.extensions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.midorinext.android.R

@Composable
internal fun AddonIcon(
    iconUrl: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    fallbackIconSize: Dp = 28.dp,
) {
    val safeIconUrl = remember(iconUrl) { iconUrl?.takeIf { it.isNotBlank() } }

    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (safeIconUrl != null) {
            AsyncImage(
                model = safeIconUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.icons_extension),
                fallback = painterResource(id = R.drawable.icons_extension),
            )
        } else {
            FallbackAddonIcon(fallbackIconSize = fallbackIconSize)
        }
    }
}

@Composable
private fun FallbackAddonIcon(fallbackIconSize: Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Icon(
            painter = painterResource(R.drawable.icons_extension),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(fallbackIconSize),
        )
    }
}



