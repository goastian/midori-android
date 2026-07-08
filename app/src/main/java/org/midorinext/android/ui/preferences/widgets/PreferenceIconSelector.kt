package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.midorinext.android.R

data class PreferenceIconSelectorOption<T> (
    @StringRes val label: Int,
    val value: T,
    @DrawableRes val icon: Int? = null,
    val tintIcon: Boolean = true,
    val backgroundColor: Color? = null,
    val iconColor: Color? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PreferenceIconSelector(
    options: List<PreferenceIconSelectorOption<T>>,
    selectedValue: T,
    onSelected: (T) -> Unit,
    shape: Shape = CircleShape,
    showSelectedIcon: Boolean = false
) {
    val localContentColor = LocalContentColor.current

    FlowRow(
        modifier = Modifier
            .selectableGroup()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            val isSelected = (option.value == selectedValue)
            val borderColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(shape = shape)
                    .border(2.dp, borderColor, shape)
                    .padding(4.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelected(option.value) }
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(option.backgroundColor ?: Color.Transparent, shape)
                ) {
                    option.icon?.let { icon ->
                        Image(
                            painter = painterResource(id = icon),
                            contentDescription = stringResource(option.label),
                            colorFilter = if (option.tintIcon) {
                                ColorFilter.tint(option.iconColor ?: localContentColor)
                            } else null,
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (isSelected && showSelectedIcon) {
                        Icon(
                            painter = painterResource(id = R.drawable.icons_check),
                            contentDescription = "selected",
                            tint = localContentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}