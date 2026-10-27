package org.midorinext.android.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.midorinext.android.R
import org.midorinext.android.preferences.app.SearchEnginePreference
import org.midorinext.android.usecases.SearchEngineOption

private fun SearchEnginePreference.logoResource(): Int? = when (this) {
    SearchEnginePreference.ECOSIA -> R.drawable.icons_engine_ecosia
    SearchEnginePreference.QWANT -> R.drawable.icons_engine_qwant
    SearchEnginePreference.STARTPAGE -> R.drawable.icons_engine_startpage
    SearchEnginePreference.BING -> R.drawable.icons_engine_bing
    SearchEnginePreference.GOOGLE -> R.drawable.icons_engine_google
    SearchEnginePreference.DUCKDUCKGO -> R.drawable.icons_engine_duckduckgo
    SearchEnginePreference.ASTIANGO, SearchEnginePreference.UNRECOGNIZED -> null
}

/**
 * Round logo that identifies a search engine. Built-in engines use bundled vectors so the icon is
 * available offline and never triggers a network request. Custom engines show their initial.
 */
@Composable
fun SearchEngineIcon(option: SearchEngineOption, modifier: Modifier = Modifier) {
    val builtIn = option.builtIn
    val logo = builtIn?.logoResource()
    when {
        builtIn == SearchEnginePreference.ASTIANGO ->
            MidoriIconOnBackground(shape = CircleShape, modifier = modifier)
        logo != null -> Box(
            modifier = modifier
                .background(Color.White, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(logo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.62f),
            )
        }
        else -> Box(
            modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = option.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
