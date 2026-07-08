package org.midorinext.android.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import org.midorinext.android.R
import mozilla.components.browser.icons.BrowserIcons
import mozilla.components.browser.icons.compose.Loader
import mozilla.components.browser.icons.compose.Placeholder
import mozilla.components.browser.icons.compose.WithIcon

@Composable
fun UrlIcon(
    browserIcons: BrowserIcons,
    url: String?,
    modifier: Modifier = Modifier
) {
    url?.let {
        browserIcons.Loader(it) {
            WithIcon { icon ->
                Image(painter = icon.painter, contentDescription = "icon", modifier = modifier)
            }
            Placeholder {
                UrlIconPlaceholder()
            }
        }
    } ?: UrlIconPlaceholder()
}

@Composable
fun UrlIconPlaceholder(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.icons_internet),
        contentDescription = "icon",
        modifier = modifier
    )
}