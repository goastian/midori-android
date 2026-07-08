package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RadioButtonOptionWithDescription<T>(
    val value: T,
    @StringRes val label: Int,
    @StringRes val description: Int? = null,
    val icon: @Composable () -> Unit = {}
)

@Composable
fun <T> PreferenceRadioButtonSelectorWithDescription(
    options: List<RadioButtonOptionWithDescription<T>>,
    value: T,
    onValueChange: (T) -> Unit
) {
    Column {
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onValueChange(option.value) }
                    .padding(8.dp)
            ) {
                RadioButton(
                    selected = (value == option.value),
                    onClick = { onValueChange(option.value) },
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(option.label),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    if (option.description != null) {
                        Text(
                            text = stringResource(option.description),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    option.icon()
                }
            }
        }
    }
}

@Composable
fun <T> PreferenceRadioSelectionPopupWithDescription(
    @StringRes label: Int,
    options: List<RadioButtonOptionWithDescription<T>>,
    value: T,
    onValueChange: (T) -> Unit
) {
    PreferenceSelectionPopup(
        label = label,
        description = null,
        popupContent = { PreferenceRadioButtonSelectorWithDescription(
            options = options,
            value = value,
            onValueChange = onValueChange
        )},
        fullscreenPopup = true
    )
}
