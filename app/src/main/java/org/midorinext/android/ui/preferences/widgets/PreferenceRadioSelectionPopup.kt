package org.midorinext.android.ui.preferences.widgets

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

@Composable
fun <T> PreferenceRadioSelectionPopup(
    @StringRes label: Int,
    options: List<RadioButtonOption<T>>, // Map<T, Int>,
    value: T,
    onValueChange: (T) -> Unit
) {
    PreferenceSelectionPopup(
        label = label,
        description = options.find { it.value == value }?.label,
        popupContent = { PreferenceRadioButtonSelector(
            options = options,
            value = value,
            onValueChange = onValueChange
        )}
    )
}

