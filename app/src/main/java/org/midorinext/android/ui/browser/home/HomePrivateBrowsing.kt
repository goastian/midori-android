package org.midorinext.android.ui.browser.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.midorinext.android.R

@Composable
fun HomePrivateBrowsing(
    onSwitchBrowsingMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    Surface(modifier = modifier
        .fillMaxSize()
        .testTag("native_private_home")
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            focusManager.clearFocus()
        })
    {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NativeHomeHeader(private = true, onSwitchBrowsingMode = onSwitchBrowsingMode)
            HomePrivateBrowsingContent(Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
fun HomePrivateBrowsingContent(
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    iconScale: Float = 1f,
    iconPaddingBottom: Dp = 20.dp,
    titleFontSize: TextUnit = 24.sp,
    titleLineHeight: TextUnit = 30.sp,
    textFontSize: TextUnit = 14.sp,
    textLineHeight: TextUnit = 22.sp,
) {
    Column(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.icons_privacy_mask_small),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color = iconColor),
            modifier = Modifier
                .size(width = 80.dp * iconScale, height = 100.dp * iconScale)
                .padding(bottom = iconPaddingBottom)
        )
        Text(
            text = stringResource(id = R.string.privatebrowsing_title),
            style = MaterialTheme.typography.headlineSmall,
            fontSize = titleFontSize,
            lineHeight = titleLineHeight,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = stringResource(id = R.string.privatebrowsing_paragraph_1),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textFontSize,
            lineHeight = textLineHeight,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            text = stringResource(id = R.string.privatebrowsing_paragraph_2),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = textFontSize,
            lineHeight = textLineHeight,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}
